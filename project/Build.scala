import sbt._
import Keys._
import com.github.joprice.Jni
import Jni.Keys._
import com.typesafe.sbt.osgi.SbtOsgi.{ OsgiKeys, osgiSettings, defaultOsgiSettings }

object ZstdBuild extends Build {

  lazy val buildVersion = "0.4.2"

  lazy val root = Project(id="zstd-jni", base = file(".")).settings(
      Jni.settings : _*
    ).settings(
      scalaVersion := "2.11.7",
      version := buildVersion,
      libraryDependencies ++= Seq(
        "org.scalatest"  %% "scalatest"  % "2.2.4"  % "test",
        "org.scalacheck" %% "scalacheck" % "1.12.5" % "test"
      ),
      libraryName := "libzstd",
      gccFlags ++= Seq(
            "-std=c99", "-Wundef", "-Wshadow", "-Wcast-align", "-Wstrict-prototypes",
            "-Wno-unused-variable", "-DZSTD_LEGACY_SUPPORT=0"
          ) ++ (System.getProperty("os.arch") match {
            case "amd64"|"x86_64"   => Seq("-msse4")
            case "i386"             => Seq("-msse4")
            case _                  => Seq()
          }),
      nativeCompiler := "gcc",
      includes += "-I" + nativeSource.value.toString,
      cppExtensions := Seq(".c"),
      cpp11 := false,
      jniClasses := Seq(
          "com.github.luben.zstd.Zstd",
          "com.github.luben.zstd.ZstdInputStream",
          "com.github.luben.zstd.ZstdOutputStream"
      ),
      binPath := {
        val os = System.getProperty("os.name").toLowerCase.replace(' ','_')
        val arch =  System.getProperty("os.arch")
        (target in Compile).value / "classes" / os / arch
      },
      headersPath := (target in Compile).value / "classes" / "include",
      publishMavenStyle := true,
      autoScalaLibrary := false,
      crossPaths := false,
      parallelExecution in Test := false,

      publishTo := {
        val nexus = "https://oss.sonatype.org/"
        if (buildVersion.trim.endsWith("SNAPSHOT"))
            Some("snapshots" at nexus + "content/repositories/snapshots")
        else
            Some("releases" at nexus + "service/local/staging/deploy/maven2")
      },

      publishMavenStyle := true,
      publishArtifact in Test := false,
      pomIncludeRepository := { _ => false },
      organization := "com.github.luben",
      licenses := Seq("BSD 2-Clause License" -> url("https://opensource.org/licenses/BSD-2-Clause")),
      description := "JNI bindings for Zstd native library that provides fast and high " +
                      "compression lossless algorithm for Java and all JVM languages.",
      pomExtra := (
        <url>https://github.com/luben/zstd-jni</url>
        <scm>
          <url>git@github.com:luben/zstd-jni.git</url>
          <connection>scm:git:git@github.com:luben/zstd-jni.git</connection>
        </scm>
        <developers>
          <developer>
            <id>karavelov</id>
            <name>Luben Karavelov</name>
            <email>karavelov@gmail.com</email>
            <organization>com.github.luben</organization>
            <organizationUrl>https://github.com/luben</organizationUrl>
          </developer>
        </developers>
      )
    ).settings(defaultOsgiSettings: _*).settings(
      OsgiKeys.bundleSymbolicName := "com.github.luben.zstd-jni",
      OsgiKeys.privatePackage := Seq("com.github.luben.zstd.util"),
      OsgiKeys.exportPackage  := Seq(s"""com.github.luben.zstd;version="$buildVersion"""")
    )
}
