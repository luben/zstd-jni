package com.sandinh.javamodule.moduleinfo

import com.sandinh.javamodule.moduleinfo.Utils.*
import org.jetbrains.annotations.NotNull
import sbt.*
import sbt.Keys.*
import sbt.librarymanagement.Configurations.{Compile, Runtime}

import java.util.jar.{JarEntry, JarInputStream, JarOutputStream}
import java.util.zip.ZipException
import scala.io.Codec.UTF8
import scala.io.Source

object ModuleTransform {
  private val JarSignaturePath = "^META-INF/[^/]+\\.(SF|RSA|DSA|sf|rsa|dsa)$".r.pattern
  private val ServicesPrefix = "META-INF/services/"

  /** Convenient class to wrap providers and packages arguments */
  private case class PP(providers: Map[String, List[String]], packages: Set[String])
  private object PP { val empty: PP = PP(Map.empty, Set.empty) }

  def addAutomaticModuleName(
      artifacts: Classpath,
      originalJar: File,
      moduleJar: File,
      automaticModule: AutomaticModule
  ): Unit = originalJar
    .ensuring(_ != moduleJar, s"moduleJar must != originalJar:\n$moduleJar\n$originalJar")
    .jarInputStream { jis =>
      val man = jis.getOrCreateManifest
      man.getMainAttributes.putValue("Automatic-Module-Name", automaticModule.moduleName)
      moduleJar.jarOutputStream(man) { jos =>
        val pp = copyAndExtractProviders(jis, jos, automaticModule.mergedJars.nonEmpty, PP.empty)
        mergeJars(artifacts, automaticModule, jos, pp)
      }
    }

  def addModuleDescriptor(
      args: ModuleInfoArgs,
      compileDepsMap: Map[String, Set[String]],
      runtimeDepsMap: Map[String, Set[String]],
      originalJar: File,
      moduleJar: File,
      info: JpmsModule,
  ): Unit = {
    def requires: Set[(String, Require)] = {
      val compileDeps = compileDepsMap.getOrElse(info.id, Set.empty)
      val runtimeDeps = runtimeDepsMap.getOrElse(info.id, Set.empty)
      if (
        compileDeps.isEmpty && runtimeDeps.isEmpty &&
        args.artifacts.forall(_.get(moduleID.key).get.jmodId != info.id)
      )
        throw new RuntimeException(s"Cannot find dependencies for '${info.moduleName}'")
      (for {
        id <- compileDeps ++ runtimeDeps
        if !info.mergedJars.contains(id)
      } yield {
        val moduleName = args.idToModuleName(id)
        if (compileDeps.contains(id) && runtimeDeps.contains(id)) List(moduleName -> Require.Transitive)
        else if (runtimeDeps.contains(id)) List(moduleName -> Require.Default)
        else if (compileDeps.contains(id)) List(moduleName -> Require.Static)
        else Nil
      }).flatten
    }
    def versionFromArts = args.artifacts.collectFirst {
      case a if a.data == originalJar => a.get(moduleID.key).get.revision
    }
    originalJar
      .ensuring(_ != moduleJar, s"moduleJar must != originalJar:\n$moduleJar\n$originalJar")
      .jarInputStream { jis =>
        moduleJar.jarOutputStream(jis.getManifest) { jos =>
          var pp = copyAndExtractProviders(jis, jos, info.mergedJars.nonEmpty, PP.empty)
          pp = mergeJars(args.artifacts, info, jos, pp)
          jos.addModuleInfo(
            info.copy(
              moduleVersion = Option(info.moduleVersion).orElse(versionFromArts).orNull,
              providers = pp.providers.filterKeys(name => !info.ignoreServiceProviders.contains(name)),
              requires =
                if (!info.requireAll) info.requires
                else info.requires ++ requires,
              exports =
                if (!info.exportAll) info.exports
                else info.exports ++ pp.packages
            ),
          )
        }
      }
  }

  private def copyAndExtractProviders(
      jis: JarInputStream,
      jos: JarOutputStream,
      willMergeJars: Boolean,
      pp: PP
  ): PP = {
    var PP(providers, packages) = pp
    jis.lazyList.foreach { jarEntry =>
      val content = IO.readBytes(jis)
      val entryName = jarEntry.getName
      val isFileInServicesFolder = entryName.startsWith(ServicesPrefix) && entryName != ServicesPrefix
      if (isFileInServicesFolder) {
        val key = entryName.substring(ServicesPrefix.length)
        val isServiceProviderFile = !key.contains("/") // ignore files in sub-folders
        if (isServiceProviderFile) {
          providers = providers.updated(
            key,
            providers.getOrElse(key, Nil) ++ extractImplementations(content)
          )
        }
      }
      if (
        !JarSignaturePath.matcher(entryName).matches && "META-INF/MANIFEST.MF" != jarEntry.getName &&
        (!willMergeJars || !isFileInServicesFolder) // service provider files will be merged later
      ) {
        jarEntry.setCompressedSize(-1)
        try {
          jos.putNextEntry(jarEntry)
          jos.write(content)
          jos.closeEntry()
        } catch {
          case e: ZipException =>
            if (!e.getMessage.startsWith("duplicate entry:")) throw new RuntimeException(e)
        }
        if (entryName.endsWith(".class")) {
          val i = entryName.lastIndexOf("/")
          if (i > 0) packages += entryName.substring(0, i)
        }
      }
    }
    PP(providers, packages)
  }

  private def extractImplementations(content: Array[Byte]) =
    Source
      .fromBytes(content)(UTF8)
      .getLines()
      .map(_.trim)
      .filter { line => line.nonEmpty && !line.startsWith("#") }
      .toList
      .distinct

  private def mergeJars(
      artifacts: Classpath,
      moduleSpec: ModuleSpec,
      outputStream: JarOutputStream,
      pp0: PP
  ): PP = {
    if (moduleSpec.mergedJars.isEmpty) return pp0
    var pp = pp0
    for (identifier <- moduleSpec.mergedJars) {
      artifacts.collectFirst {
        case a if a.get(moduleID.key).get.jmodId == identifier => a.data
      } match {
        case Some(mergeJarFile) =>
          mergeJarFile.jarInputStream { jis =>
            pp = copyAndExtractProviders(jis, outputStream, willMergeJars = true, pp)
          }
        case None => throw new RuntimeException("Jar not found: " + identifier)
      }
    }
    mergeServiceProviderFiles(outputStream, pp.providers)
    pp
  }

  private def mergeServiceProviderFiles(jos: JarOutputStream, providers: Map[String, List[String]]): Unit =
    providers.foreach { case (k, v) =>
      jos.putNextEntry(new JarEntry(ServicesPrefix + k))
      for (implementation <- v) {
        jos.write(implementation.getBytes)
        jos.write("\n".getBytes)
      }
      jos.closeEntry()
    }
}

private class ModuleInfoArgs(infos: Seq[ModuleSpec], jarTypes: Set[String], up: UpdateReport) {
  private def knowns = artifacts.flatMap { a =>
    def id = a.get(moduleID.key).get.jmodId
    a.data.moduleName.map(KnownModule(_, id)).toSeq
  }
  private lazy val moduleNameMap = (knowns ++ infos).map(m => m.id -> m.moduleName).toMap
  def idToModuleName(@NotNull id: String): String = moduleNameMap.getOrElse(
    id,
    throw new RuntimeException(
      s"""The module name of the following dependency is not known: $id
       | - If it is an external legacy dependency, patch it to a 'JpmsModule' or 'AutomaticModule', eg:
       |   `Global / moduleInfos += JpmsModule("paranamer", "com.thoughtworks.paranamer:paranamer")`
       | - If it is your own sbt project, eg `myPrj`, set moduleInfo for it, eg:
       |   `myPrj = project.settings(moduleInfo := AutomaticModule("com.myprj"))`""".stripMargin
    )
  )
  // @see managedClasspath
  lazy val artifacts: Classpath = up
    .filter(
      configurationFilter(Compile.name | Runtime.name) && artifactFilter(`type` = jarTypes)
    )
    .toSeq
    .map { case (_, module, _, file) =>
      Attributed(file)(AttributeMap.empty.put(moduleID.key, module))
    }
    .distinct
}
