import sbt._
import Keys._
import com.github.joprice.Jni
import Jni.Keys._
import java.io.File

object ZstdBuild extends Build {

  lazy val buildVersion = "0.1.3"

  lazy val root = Project(id="zstd-jni", base = file("."), settings = Project.defaultSettings).
  settings(
    Jni.settings : _*
  ).settings(
    scalaVersion := "2.11.7",
    version := buildVersion,
    organization := "com.github.luben",
    libraryName := "libzstd",
    libraryDependencies ++= Seq(
      "org.scalatest"  %% "scalatest"  % "2.2.4"  % "test",
      "org.scalacheck" %% "scalacheck" % "1.12.5" % "test"
    ),
    gccFlags ++= Seq(
          "-std=c99", "-Wundef", "-Wshadow", "-Wcast-align", "-Wstrict-prototypes",
          "-Wno-unused-variable",
          "-O2",
          //"-funroll-loops",
          "-DZSTD_LEGACY_SUPPORT=0"
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
    parallelExecution in Test := false
  )
}
