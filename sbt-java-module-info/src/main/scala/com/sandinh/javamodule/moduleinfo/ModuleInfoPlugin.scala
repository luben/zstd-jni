package com.sandinh.javamodule.moduleinfo

import sbt.*
import sbt.Keys.*
import Utils.*
import ModuleTransform.{addAutomaticModuleName, addModuleDescriptor}
import sbt.Package.ManifestAttributes
import sbt.internal.BuildDependencies
import sbt.io.FileFilter
import sbt.librarymanagement.Configurations.RuntimeInternal
import sbt.sandinh.DependencyTreeAccess
import sbt.sandinh.DependencyTreeAccess.{moduleInfoDepGraph, toDepsMap}

object ModuleInfoPlugin extends AutoPlugin {
  object autoImport {
    val moduleInfo = settingKey[ModuleSpec](
      "JpmsModule or AutomaticModule used to generate module-info.class or set Automatic-Module-Name field in MANIFEST.MF for this project"
    )
    val moduleInfoGenClass = taskKey[Option[File]]("Generate module-info.class")
    val moduleInfos = settingKey[Seq[ModuleSpec]](
      "extra Java module-info to patch non-module jar dependencies"
    )
    val moduleInfoFailOnMissing = settingKey[Boolean](
      "Fail update task if exist non-module jar dependencies and no ModuleSpec is defined for it"
    )
  }
  import autoImport.*

  override def globalSettings: Seq[Def.Setting[?]] = Seq(
    moduleInfos := Nil,
    moduleInfoFailOnMissing := false,
  )

  override def projectSettings: Seq[Setting[?]] = DependencyTreeAccess.settings ++ Seq(
    Compile / packageBin / packageOptions ++= (moduleInfo.value match {
      case AutomaticModule(moduleName, _, _) =>
        ManifestAttributes("Automatic-Module-Name" -> moduleName) +: Nil
      case _ => Nil
    }),
    moduleInfoGenClass := genModuleInfoClass.value,
    // filter(_ => false) so we don't add classes/module-info.class file to `products` which contains directories
    Compile / products ++= moduleInfoGenClass.value.filter(_ => false),
    update := transformUpdateReport.value(update.value),
  )

  /** moduleInfo of inter-project dependencies */
  private def dependentProjectModules = Def.settingDyn[Seq[ModuleSpec]] {
    val deps = dependentProjects(buildDependencies.value, thisProjectRef.value)
    (deps.map(_ / moduleInfo).join zip
      deps.map(_ / projectID).join zip
      deps.map(_ / scalaModuleInfo).join) { case ((ms, ids), iss) =>
      (ms zip ids zip iss).map { case ((m, id), is) =>
        m.withDefaultId(id.jmodId(is))
      }
    }
  }

  /** All inter-project that `ref` dependsOn, transitively */
  private def dependentProjects(buildDeps: BuildDependencies, ref: ProjectRef): Seq[ProjectRef] = {
    val refs = buildDeps.classpath(ref).collect {
      case d if isRuntimeDepend(d.configuration) => d.project
    }
    (refs ++ refs.flatMap(dependentProjects(buildDeps, _)) // also find transitive deps
    ).distinct
  }

  private def transformUpdateReport = Def.task[UpdateReport => UpdateReport] {
    val log = streams.value.log
    val infos = moduleInfos.value ++ dependentProjectModules.value
    val out = (ThisBuild / baseDirectory).value / "target/moduleInfo"
    out.mkdirs()
    val failOnMissing = moduleInfoFailOnMissing.value
    val compileDepsMap = toDepsMap((Compile / moduleInfoDepGraph).value)
    val runtimeDepsMap = toDepsMap((Runtime / moduleInfoDepGraph).value)
    val clsTypes = classpathTypes.value

    (report: UpdateReport) => {
      val args = new ModuleInfoArgs(infos, clsTypes, report)
      val idToJar: Map[String, Option[File]] = report
        .configuration(RuntimeInternal)
        .get
        .modules
        .filter(_.artifacts.nonEmpty)
        .map { mod =>
          val id = mod.module.jmodId
          if (infos.exists(_.mergedJars.contains(id))) id -> None
          else {
            val originalJar = jarOf(mod)
            val moduleJar = out / originalJar.name
            val remappedJar = infos.find(_.id == id) match {
              case Some(info) if originalJar.moduleName.exists(_ != info.moduleName) =>
                sys.error(
                  s"$id:${originalJar.name} is already a module. Reject transforming moduleName to ${info.moduleName}"
                )
              case Some(_: KnownModule) => Some(originalJar)
              case Some(info: JpmsModule) =>
                originalJar.jpmsModuleName.foreach { n =>
                  log.err(
                    s"$id:${originalJar.name} is already a jpms module $n. Still transform to $moduleJar"
                  )
                }
                genIfNotExist(
                  moduleJar,
                  addModuleDescriptor(args, compileDepsMap, runtimeDepsMap, originalJar, _, info)
                )
                Some(moduleJar)
              case Some(info: AutomaticModule) =>
                originalJar.jpmsModuleName
                  .map { n =>
                    log.err(
                      s"$id:${originalJar.name} is already a jpms module $n. Reject transforming to AutomaticModule ${info.moduleName}"
                    )
                    originalJar
                  }
                  .orElse {
                    if (originalJar.autoModuleName.isDefined && info.mergedJars.isEmpty) {
                      Some(originalJar)
                    } else {
                      genIfNotExist(
                        moduleJar,
                        addAutomaticModuleName(args.artifacts, originalJar, _, info)
                      )
                      Some(moduleJar)
                    }
                  }
              case None =>
                if (originalJar.moduleName.isDefined) Some(originalJar)
                else if (!failOnMissing) {
                  log.warn(s"Not a module and no mapping defined: $id -> $originalJar")
                  Some(originalJar)
                } else
                  sys.error(s"Not a module and no mapping defined: $id -> $originalJar")
            }
            id -> remappedJar
          }
        }
        .toMap
      report.withConfigurations(report.configurations.map { c =>
        c.withModules(c.modules.flatMap { mod =>
          idToJar.get(mod.module.jmodId) match {
            case None                    => mod +: Nil
            case Some(None)              => Nil
            case Some(Some(remappedJar)) => withJar(mod, remappedJar) +: Nil
          }
        })
      })
    }
  }

  private def dirs(d: File, filter: FileFilter) = PathFinder(d)
    .glob(DirectoryFilter)
    .globRecursive(filter)
    .get()
    .map { f => IO.relativize(d, f.getParentFile).get }
    .toSet

  /** Generate module-info.class in Compile / classDirectory */
  private def genModuleInfoClass: Def.Initialize[Task[Option[File]]] = Def.taskDyn {
    moduleInfo.value match {
      case _: KnownModule | _: AutomaticModule => Def.task(None)
      case info0: JpmsModule =>
        Def.taskDyn {
          val classDir = (Compile / classDirectory).value
          val f = classDir / "module-info.class"
          val info1 =
            if (!info0.exportAll) info0
            else info0.copy(exports = dirs(classDir, "*.class") ++ info0.exports)
          val info = info1.copy(
            moduleVersion = Option(info1.moduleVersion).getOrElse(version.value),
            mainClass = Option(info1.mainClass).getOrElse((Compile / run / mainClass).value)
          )

          Def.taskIf {
            if (info.requireAll) {
              val is = scalaModuleInfo.value
              val args = new ModuleInfoArgs(
                moduleInfos.value ++ dependentProjectModules.value,
                classpathTypes.value,
                update.value
              )
              val requires = allDependencies.value.flatMap { m =>
                if (isRuntimeDepend(m.configurations)) {
                  val tpe = if (m.isTransitive) Require.Transitive else Require.Default
                  val require = args.idToModuleName(m.jmodId(is)) -> tpe
                  require +: Nil
                } else Nil
              }.toSet
              genIfNotExist(f, IO.write(_, info.copy(requires = info.requires ++ requires).toModuleInfoClass))
              Some(f)
            } else {
              genIfNotExist(f, IO.write(_, info.toModuleInfoClass))
              Some(f)
            }
          }
        }
    }
  }

  // TODO cache and re-generate when needed
  private def genIfNotExist(f: File, gen: File => Unit): Unit =
    if (!f.isFile) gen(f)

  private def jarOf(mod: ModuleReport) = mod.artifacts.collect {
    case (a, f) if a.extension == "jar" => f
  } match {
    case Vector(f) => f
    case arts      => sys.error(s"Cant find art for ${mod.module.jmodId}. $arts")
  }
  private def withJar(mod: ModuleReport, remappedJar: File) = mod.withArtifacts(mod.artifacts.map {
    case x @ (a, _) if a.extension != "jar" => x
    case (a, _)                             => a -> remappedJar
  })
}
