package com.sandinh.javamodule.moduleinfo

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassReader.*
import org.objectweb.asm.tree.ClassNode
import sbt.{CrossVersion, File, ModuleID}
import sbt.io.Using
import sbt.librarymanagement.ScalaModuleInfo

import java.lang.Boolean.parseBoolean
import java.nio.file.Files
import java.util.jar.{JarEntry, JarInputStream, JarOutputStream, Manifest}
import scala.collection.compat.immutable.LazyList

object Utils {
  def toSlash(fqdn: String): String = fqdn.replace('.', '/')
  private def configurationContains(configurations: Option[String], c: String) =
    configurations.forall { cs =>
      cs.split(';').map(_.replace(" ", "")).exists(s => s == c || s.startsWith(s"$c->"))
    }
  private[moduleinfo] def isRuntimeDepend(configurations: Option[String]): Boolean =
    configurationContains(configurations, "compile") || configurationContains(configurations, "runtime")

  implicit final class ModuleIDOps(val m: ModuleID) extends AnyVal {
    def jmodId: String = s"${m.organization}:${m.name}"
    def jmodId(is: Option[ScalaModuleInfo]): String =
      CrossVersion(m, is).getOrElse(identity[String] _)(m.jmodId)
  }

  implicit final class JarOutputStreamOps(val jos: JarOutputStream) extends AnyVal {
    def addModuleInfo(info: JpmsModule): Unit = {
      jos.putNextEntry(new JarEntry("module-info.class"))
      jos.write(info.toModuleInfoClass)
      jos.closeEntry()
    }
  }
  implicit final class JarInputStreamOps(val jis: JarInputStream) extends AnyVal {
    def lazyList: LazyList[JarEntry] = LazyList.continually(jis.getNextJarEntry).takeWhile(_ != null)

    def getOrCreateManifest: Manifest = jis.getManifest match {
      case null =>
        val m = new Manifest
        m.getMainAttributes.putValue("Manifest-Version", "1.0")
        m
      case m => m
    }

    def jpmsModuleName: Option[String] = {
      val multi = Option(jis.getManifest).fold(false)(_.isMultiRelease)
      jis.lazyList
        .find { e =>
          e.getName == "module-info.class" ||
          multi && ModuleInfoClassMjarPath.matcher(e.getName).matches()
        }
        .map { _ =>
          val cr = new ClassReader(jis)
          val classNode = new ClassNode
          cr.accept(classNode, SKIP_CODE | SKIP_DEBUG | SKIP_FRAMES)
          classNode.module.name
        }
    }

    def autoModuleName: Option[String] = Option(jis.getManifest).flatMap(_.autoModuleName)
    def moduleName: Option[String] = jpmsModuleName.orElse(autoModuleName)
  }

  private val ModuleInfoClassMjarPath = "META-INF/versions/\\d+/module-info.class".r.pattern

  implicit final class ManifestOps(val m: Manifest) extends AnyVal {
    def autoModuleName: Option[String] = Option(m.getMainAttributes.getValue("Automatic-Module-Name"))
    def isMultiRelease: Boolean = parseBoolean(m.getMainAttributes.getValue("Multi-Release"))
  }

  implicit final class JarFileOps(val jar: File) extends AnyVal {
    def jarInputStream[R](f: JarInputStream => R): R =
      Using.jarInputStream(Files.newInputStream(jar.toPath))(f)

    /** @note `jar` file will be open using CREATE & WRITE & TRUNCATE_EXISTING StandardOpenOption */
    def jarOutputStream[R](man: Manifest = null)(f: JarOutputStream => R): R =
      Using.resource { (f: File) =>
        val out = Files.newOutputStream(f.toPath)
        if (man == null) new JarOutputStream(out)
        else new JarOutputStream(out, man)
      }(jar)(f)

    def addAutomaticModuleName(moduleName: String): File = {
      import java.nio.file.StandardCopyOption.*
      val tmpJar = Files.createTempFile(null, null).toFile
      ModuleTransform.addAutomaticModuleName(Nil, jar, tmpJar, AutomaticModule(moduleName))
      Files.move(tmpJar.toPath, jar.toPath, REPLACE_EXISTING, ATOMIC_MOVE)
      jar
    }

    def jpmsModuleName: Option[String] = jarInputStream(_.jpmsModuleName)
    def autoModuleName: Option[String] = jarInputStream(_.autoModuleName)
    def moduleName: Option[String] = jarInputStream(_.moduleName)
  }
}
