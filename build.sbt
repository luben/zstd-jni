
name := "zstd-jni"

version := "0.5.0"

scalaVersion := "2.11.7"

enablePlugins(JniPlugin, SbtOsgi)

autoScalaLibrary := false

crossPaths := false

parallelExecution in Test := false

libraryDependencies ++= Seq(
  "org.scalatest"  %% "scalatest"  % "2.2.6"  % "test",
  "org.scalacheck" %% "scalacheck" % "1.12.5" % "test"
)

// JNI
jniLibraryName := "zstd"

jniNativeClasses := Seq(
  "com.github.luben.zstd.Zstd",
  "com.github.luben.zstd.ZstdInputStream",
  "com.github.luben.zstd.ZstdOutputStream"
)

jniLibSuffix := (System.getProperty("os.name").toLowerCase.replace(' ', '_') match {
  case os if os.contains("os_x")   => "dylib"
  case os if os.contains("darwin") => "dylib"
  case os if os.contains("win")    => "dll"
  case _  => "so"
})

jniNativeCompiler := "cc"

jniCppExtensions := Seq("c")

jniGccFlags ++= Seq(
  "-std=c99", "-Wundef", "-Wshadow", "-Wcast-align", "-Wstrict-prototypes",
  "-Wno-unused-variable", "-DZSTD_LEGACY_SUPPORT=0"
) ++ (System.getProperty("os.arch") match {
  case "amd64"|"x86_64"   => Seq("-msse4")
  case "i386"             => Seq("-msse4")
  case _                  => Seq()
})

jniIncludes += "-I" + jniNativeSources.value.toString

jniUseCpp11 := false

jniBinPath := {
  val os = System.getProperty("os.name").toLowerCase.replace(' ','_') match {
    case os if os.startsWith("win") => "win"
    case os if os.startsWith("mac") => "darwin"
    case os => os
  }
  val arch = System.getProperty("os.arch")
  (target in Compile).value / "classes" / os / arch
}

jniHeadersPath := (target in Compile).value / "classes" / "include"

// JaCoCo

import de.johoop.jacoco4sbt._

jacoco.settings

jacoco.reportFormats in jacoco.Config := Seq(
  XMLReport(encoding = "utf-8"),
  ScalaHTMLReport(withBranchCoverage = true)
)

// Sonatype

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (version.value.toString.trim.endsWith("SNAPSHOT"))
      Some("snapshots" at nexus + "content/repositories/snapshots")
  else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

organization := "com.github.luben"

licenses := Seq("BSD 2-Clause License" -> url("https://opensource.org/licenses/BSD-2-Clause"))

description := "JNI bindings for Zstd native library that provides fast and high " +
		"compression lossless algorithm for Java and all JVM languages."

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

// OSGI

osgiSettings

OsgiKeys.bundleSymbolicName := "com.github.luben.zstd-jni"
OsgiKeys.exportPackage  := Seq(s"""com.github.luben.zstd;version="${version.value}"""")
OsgiKeys.privatePackage := Seq("com.github.luben.zstd.util", "include",
  "linux.amd64", "linux.i386", "linux.aarch64", "linux.ppc64",
  "netbsd.amd64", "aix.ppc64", "darwin.x86_64", "win.amd64"
)
