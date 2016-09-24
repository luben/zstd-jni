
name := "zstd-jni"

version := "1.0.0"

scalaVersion := "2.11.8"

enablePlugins(JniPlugin, SbtOsgi)

autoScalaLibrary := false

crossPaths := false

parallelExecution in Test := false

libraryDependencies ++= Seq(
  "org.scalatest"  %% "scalatest"  % "2.2.6"  % "test",
  "org.scalacheck" %% "scalacheck" % "1.12.5" % "test"
)

// sbt-jni configuration
jniLibraryName := "zstd"

jniNativeClasses := Seq(
  "com.github.luben.zstd.Zstd",
  "com.github.luben.zstd.ZstdDictCompress",
  "com.github.luben.zstd.ZstdDictDecompress",
  "com.github.luben.zstd.ZstdOutputStream",
  "com.github.luben.zstd.ZstdInputStream"
)

jniLibSuffix := (System.getProperty("os.name").toLowerCase match {
  case os if os startsWith "mac"    => "dylib"
  case os if os startsWith "darwin" => "dylib"
  case os if os startsWith "win"    => "dll"
  case _                            => "so"
})

jniNativeCompiler := "gcc"

jniUseCpp11 := false

jniCppExtensions := Seq("c")

jniGccFlags ++= Seq(
  "-std=c99", "-Wundef", "-Wshadow", "-Wcast-align", "-Wstrict-prototypes",
  "-Wno-unused-variable", "-DZSTD_LEGACY_SUPPORT=1"
) ++ (System.getProperty("os.arch") match {
  case "amd64"|"x86_64"   => Seq("-msse4")
  case "i386"             => Seq("-msse4")
  case _                  => Seq()
})

// compilation on Windows with MSYS/gcc needs extra flags in order
// to produce correct DLLs, also it alway produces position independent
// code so let's remove the flag and silence a warning
jniGccFlags := (
  if (System.getProperty("os.name").toLowerCase startsWith "win")
    jniGccFlags.value.filterNot(_ == "-fPIC") ++
      Seq("-D_JNI_IMPLEMENTATION_", "-Wl,--kill-at")
  else
    jniGccFlags.value
  )

// Special case the jni platform header on windows (use the one from the repo)
// because the JDK provided one is not compatible with the standard compliant
// compilers but only with VisualStudio - our build uses MSYS/gcc
jniJreIncludes := {
  jniJdkHome.value.fold(Seq.empty[String]) { home =>
    val absHome = home.getAbsolutePath
    if (System.getProperty("os.name").toLowerCase startsWith "win") {
      Seq(s"include").map(file => s"-I$absHome/../$file") ++
      Seq(s"""-I${sourceDirectory.value / "windows" / "include"}""")
    } else {
      val jniPlatformFolder  = System.getProperty("os.name").toLowerCase match {
        case os if os.startsWith("mac") => "darwin"
        case os                         => os
      }
      // in a typical installation, JDK files are one directory above the
      // location of the JRE set in 'java.home'
      Seq(s"include", s"include/$jniPlatformFolder").map(file => s"-I$absHome/../$file")
    }
  }
}

jniIncludes ++= Seq("-I" + jniNativeSources.value.toString,
                    "-I" + jniNativeSources.value.toString + "/common",
                    "-I" + jniNativeSources.value.toString + "/legacy"
                    )

// Where to put the compiled binaries
jniBinPath := {
  val os = System.getProperty("os.name").toLowerCase.replace(' ','_') match {
    case os if os startsWith "win" => "win"
    case os if os startsWith "mac" => "darwin"
    case os                        => os
  }
  val arch = System.getProperty("os.arch")
  (target in Compile).value / "classes" / os / arch
}

// Where to put the generated headers for the JNI lib
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
  "aix.ppc64", "darwin.x86_64", "win.amd64", "win.x86"
)
