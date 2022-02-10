val nameValue = "zstd-jni"

name := nameValue

version := {
  scala.io.Source.fromFile("version").getLines.next
}

scalaVersion := "2.12.15"

enablePlugins(JniPlugin, SbtOsgi)

autoScalaLibrary := false

crossPaths := false

logBuffered in Test := false

parallelExecution in Test := false

libraryDependencies ++= Seq(
  "org.scalatest"  %% "scalatest"  % "3.0.5"  % "test",
  "org.scalacheck" %% "scalacheck" % "1.15.4" % "test"
)

javacOptions ++= Seq("--release", "6", "-Xlint:unchecked")

javacOptions in doc := Seq("--release", "6")

// fork := true
// Check at runtime for JNI errors when running tests
javaOptions in Test ++= Seq("-Xcheck:jni")

// sbt-jni configuration
jniLibraryName := "zstd-jni" + "-" + version.value

jniNativeClasses := Seq(
  "com.github.luben.zstd.Zstd",
  "com.github.luben.zstd.ZstdCompressCtx",
  "com.github.luben.zstd.ZstdDecompressCtx",
  "com.github.luben.zstd.ZstdDictCompress",
  "com.github.luben.zstd.ZstdDictDecompress",
  "com.github.luben.zstd.ZstdOutputStreamNoFinalizer",
  "com.github.luben.zstd.ZstdInputStreamNoFinalizer",
  "com.github.luben.zstd.ZstdDirectBufferDecompressingStream",
  "com.github.luben.zstd.ZstdDirectBufferCompressingStream"
)

jniLibSuffix := (System.getProperty("os.name").toLowerCase match {
  case os if os startsWith "mac"    => "dylib"
  case os if os startsWith "darwin" => "dylib"
  case os if os startsWith "win"    => "dll"
  case _                            => "so"
})

jniNativeCompiler := Option(System.getenv("CC")).getOrElse("gcc")

jniUseCpp11 := false

jniCppExtensions := Seq("c", "S")

jniGccFlags ++= Seq(
  "-std=c99", "-Wundef", "-Wshadow", "-Wcast-align", "-Wstrict-prototypes", "-Wno-unused-variable",
  "-Wpointer-arith", "-DZSTD_LEGACY_SUPPORT=4", "-DZSTD_MULTITHREAD=1", "-lpthread", "-flto", "-static-libgcc"
)

// compilation on Windows with MSYS/gcc needs extra flags in order
// to produce correct DLLs, also it alway produces position independent
// code so let's remove the flag and silence a warning
jniGccFlags := (
  if (System.getProperty("os.name").toLowerCase startsWith "win")
    jniGccFlags.value.filterNot(_ == "-fPIC") ++
      Seq("-D_JNI_IMPLEMENTATION_", "-Wl,--kill-at")
  else if (System.getProperty("os.name").toLowerCase startsWith "mac")
    // MacOS uses clang that does not support the "-static-libgcc" option
    jniGccFlags.value.filterNot(_ == "-static-libgcc")
  else
    // the default is compilation with GCC
    jniGccFlags.value
  )

// Special case the jni platform header on windows (use the one from the repo)
// because the JDK provided one is not compatible with the standard compliant
// compilers but only with VisualStudio - our build uses MSYS/gcc
jniJreIncludes := {
  jniJdkHome.value.fold(Seq.empty[String]) { home =>
    val absHome = home.getAbsolutePath
    if (System.getProperty("os.name").toLowerCase startsWith "win") {
      Seq(s"include").map(file => s"-I$absHome/$file") ++
      Seq(s"""-I${sourceDirectory.value / "windows" / "include"}""")
    } else {
      val jniPlatformFolder  = System.getProperty("os.name").toLowerCase match {
        case os if os.startsWith("mac") => "darwin"
        case os                         => os
      }
      Seq(s"include", s"include/$jniPlatformFolder").map(file => s"-I$absHome/$file")
    }
  }
}

// Add the header files of Zstd to the include list
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

// Do no generate C header files - we don't have use of them.
// There is also a compatibility problem - newer JDKs don't have `javah`
jniGenerateHeaders := false

// Generate a class with the version
Compile / sourceGenerators += Def.task {
  val file = (Compile / sourceManaged).value / "com" / "github" / "luben" / "zstd" / "util" / "ZstdVersion.java"
  IO.write(file, "package com.github.luben.zstd.util;\n\npublic class ZstdVersion {\n\tpublic static final String VERSION = \"" + version.value + "\";\n}\n" )
  Seq(file)
}

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

packageOptions in (Compile, packageBin) ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Bundle-NativeCode") ->
  {s"""darwin/x86_64/libzstd-jni-${version.value}.dylib;osname=MacOS;osname=MacOSX;processor=x86_64,
      |darwin/aarch64/libzstd-jni-${version.value}.dylib;osname=MacOS;osname=MacOSX;processor=aarch64,
      |freebsd/amd64/libzstd-jni-${version.value}.so;osname=FreeBSD;processor=amd64,
      |freebsd/i386/libzstd-jni-${version.value}.so;osname=FreeBSD;processor=i386,
      |linux/aarch64/libzstd-jni-${version.value}.so;osname=Linux;processor=aarch64,
      |linux/amd64/libzstd-jni-${version.value}.so;osname=Linux;processor=amd64,
      |linux/arm/libzstd-jni-${version.value}.so;osname=Linux;processor=arm,
      |linux/i386/libzstd-jni-${version.value}.so;osname=Linux;processor=i386,
      |linux/mips64/libzstd-jni-${version.value}.so;osname=Linux;processor=mips64,
      |linux/loongarch64/libzstd-jni-${version.value}.so;osname=Linux;processor=loongarch64,
      |linux/ppc64/libzstd-jni-${version.value}.so;osname=Linux;processor=ppc64,
      |linux/ppc64le/libzstd-jni-${version.value}.so;osname=Linux;processor=ppc64le,
      |linux/s390x/libzstd-jni-${version.value}.so;osname=Linux;processor=s390x,
      |win/amd64/libzstd-jni-${version.value}.dll;osname=Win32;processor=amd64,
      |win/x86/libzstd-jni-${version.value}.dll;osname=Win32;processor=x86""".stripMargin}),
)

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
OsgiKeys.exportPackage  := Seq(s"com.github.luben.zstd", "com.github.luben.zstd.util")
OsgiKeys.importPackage := Seq("org.osgi.framework;resolution:=optional")
OsgiKeys.privatePackage := Seq(
    "linux.amd64", "linux.i386", "linux.aarch64", "linux.arm", "linux.ppc64",
    "linux.ppc64le", "linux.mips64", "linux.loongarch64", "linux.s390x", "darwin.x86_64",
    "darwin.aarch64", "win.amd64", "win.x86", "freebsd.amd64", "freebsd.i386"
)

// Jacoco coverage setting
jacocoReportSettings := JacocoReportSettings(
  "Jacoco Coverage Report",
  None,
  JacocoThresholds(),
  Seq(JacocoReportFormats.XML, JacocoReportFormats.HTML),
  "utf-8")

// Android .aar
val aarTask = taskKey[File]("aar Task")
aarTask := {
  import scala.sys.process._
  val aarName = s"target/${nameValue}-${version.value}.aar";
  Process("gradle",  "assembleRelease" :: Nil).!
  (file("build/outputs/aar/zstd-jni-release.aar") #> file(aarName)).!
  file(aarName)
}
addArtifact(Artifact(nameValue, "aar", "aar"), aarTask)

// classified Jars
lazy val classes = Path.selectSubpaths(file("target/classes"), new io.SimpleFilter(name => name.endsWith(".class"))).toList

lazy val Linux_amd64 = config("linux_amd64").extend(Compile)
inConfig(Linux_amd64)(Defaults.compileSettings)
mappings in (Linux_amd64, packageBin) := {
  (file(s"target/classes/linux/amd64/libzstd-jni-${version.value}.so"), s"linux/amd64/libzstd-jni-${version.value}.so") :: classes
}
packageOptions in (Linux_amd64, packageBin) ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "linux_amd64"), packageBin in Linux_amd64)

lazy val Linux_i386 = config("linux_i386").extend(Compile)
inConfig(Linux_i386)(Defaults.compileSettings)
mappings in (Linux_i386, packageBin) := {
  (file(s"target/classes/linux/i386/libzstd-jni-${version.value}.so"), s"linux/i386/libzstd-jni-${version.value}.so") :: classes
}
packageOptions in (Linux_i386, packageBin) ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "linux_i386"), packageBin in Linux_i386)

lazy val Linux_aarch64 = config("linux_aarch64").extend(Compile)
inConfig(Linux_aarch64)(Defaults.compileSettings)
mappings in (Linux_aarch64, packageBin) := {
  (file(s"target/classes/linux/aarch64/libzstd-jni-${version.value}.so"), s"linux/aarch64/libzstd-jni-${version.value}.so") :: classes
}
packageOptions in (Linux_aarch64, packageBin) ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "linux_aarch64"), packageBin in Linux_aarch64)

lazy val Linux_arm = config("linux_arm").extend(Compile)
inConfig(Linux_arm)(Defaults.compileSettings)
mappings in (Linux_arm, packageBin) := {
  (file(s"target/classes/linux/arm/libzstd-jni-${version.value}.so"), s"linux/arm/libzstd-jni-${version.value}.so") :: classes
}
packageOptions in (Linux_arm, packageBin) ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "linux_arm"), packageBin in Linux_arm)

lazy val Linux_ppc64le = config("linux_ppc64le").extend(Compile)
inConfig(Linux_ppc64le)(Defaults.compileSettings)
mappings in (Linux_ppc64le, packageBin) := {
  (file(s"target/classes/linux/ppc64le/libzstd-jni-${version.value}.so"), s"linux/ppc64le/libzstd-jni-${version.value}.so") :: classes
}
packageOptions in (Linux_ppc64le, packageBin) ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "linux_ppc64le"), packageBin in Linux_ppc64le)

lazy val Linux_ppc64 = config("linux_ppc64").extend(Compile)
inConfig(Linux_ppc64)(Defaults.compileSettings)
mappings in (Linux_ppc64, packageBin) := {
  (file(s"target/classes/linux/ppc64/libzstd-jni-${version.value}.so"), s"linux/ppc64/libzstd-jni-${version.value}.so") :: classes
}
packageOptions in (Linux_ppc64, packageBin) ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "linux_ppc64"), packageBin in Linux_ppc64)

lazy val Linux_mips64 = config("linux_mips64").extend(Compile)
inConfig(Linux_mips64)(Defaults.compileSettings)
mappings in (Linux_mips64, packageBin) := {
  (file(s"target/classes/linux/mips64/libzstd-jni-${version.value}.so"), s"linux/mips64/libzstd-jni-${version.value}.so") :: classes
}
packageOptions in (Linux_mips64, packageBin) ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "linux_mips64"), packageBin in Linux_mips64)

lazy val Linux_loongarch64 = config("linux_loongarch64").extend(Compile)
inConfig(Linux_loongarch64)(Defaults.compileSettings)
mappings in (Linux_loongarch64, packageBin) := {
  (file(s"target/classes/linux/loongarch64/libzstd-jni-${version.value}.so"), s"linux/loongarch64/libzstd-jni-${version.value}.so") :: classes
}
packageOptions in (Linux_loongarch64, packageBin) ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "linux_loongarch64"), packageBin in Linux_loongarch64)

lazy val Linux_s390x = config("linux_s390x").extend(Compile)
inConfig(Linux_s390x)(Defaults.compileSettings)
mappings in (Linux_s390x, packageBin) := {
  (file(s"target/classes/linux/s390x/libzstd-jni-${version.value}.so"), s"linux/s390x/libzstd-jni-${version.value}.so") :: classes
}
packageOptions in (Linux_s390x, packageBin) ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "linux_s390x"), packageBin in Linux_s390x)

/*
lazy val Aix_ppc64 = config("aix_ppc64").extend(Compile)
inConfig(Aix_ppc64)(Defaults.compileSettings)
mappings in (Aix_ppc64, packageBin) := {
  (file(s"target/classes/aix/ppc64/libzstd-jni-${version.value}.so"), s"aix/ppc64/libzstd-jni-${version.value}.so") :: classes
}
packageOptions in (Aix_ppc64, packageBin) ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "aix_ppc64"), packageBin in Aix_ppc64)
*/

lazy val Darwin_x86_64 = config("darwin_x86_64").extend(Compile)
inConfig(Darwin_x86_64)(Defaults.compileSettings)
mappings in (Darwin_x86_64, packageBin) := {
  (file(s"target/classes/darwin/x86_64/libzstd-jni-${version.value}.dylib"), s"darwin/x86_64/libzstd-jni-${version.value}.dylib") :: classes
}
packageOptions in (Darwin_x86_64, packageBin) ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "darwin_x86_64"), packageBin in Darwin_x86_64)

lazy val Darwin_aarch64 = config("darwin_aarch64").extend(Compile)
inConfig(Darwin_aarch64)(Defaults.compileSettings)
mappings in (Darwin_aarch64, packageBin) := {
  (file(s"target/classes/darwin/aarch64/libzstd-jni-${version.value}.dylib"), s"darwin/aarch64/libzstd-jni-${version.value}.dylib") :: classes
}
packageOptions in (Darwin_aarch64, packageBin) ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "darwin_aarch64"), packageBin in Darwin_aarch64)

lazy val FreeBSD_amd64 = config("freebsd_amd64").extend(Compile)
inConfig(FreeBSD_amd64)(Defaults.compileSettings)
mappings in (FreeBSD_amd64, packageBin) := {
  (file(s"target/classes/freebsd/amd64/libzstd-jni-${version.value}.so"), s"freebsd/amd64/libzstd-jni-${version.value}.so") :: classes
}
packageOptions in (FreeBSD_amd64, packageBin) ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "freebsd_amd64"), packageBin in FreeBSD_amd64)

lazy val FreeBSD_i386 = config("freebsd_i386").extend(Compile)
inConfig(FreeBSD_i386)(Defaults.compileSettings)
mappings in (FreeBSD_i386, packageBin) := {
  (file(s"target/classes/freebsd/i386/libzstd-jni-${version.value}.so"), s"freebsd/i386/libzstd-jni-${version.value}.so") :: classes
}
packageOptions in (FreeBSD_i386, packageBin) ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "freebsd_i386"), packageBin in FreeBSD_i386)

val Win_x86 = config("win_x86").extend(Compile)
inConfig(Win_x86)(Defaults.compileSettings)
mappings in (Win_x86, packageBin) := {
  (file(s"target/classes/win/x86/libzstd-jni-${version.value}.dll"), s"win/x86/libzstd-jni-${version.value}.dll") :: classes
}
packageOptions in (Win_x86, packageBin) ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "win_x86"), packageBin in Win_x86)

val Win_amd64 = config("win_amd64").extend(Compile)
inConfig(Win_amd64)(Defaults.compileSettings)
mappings in (Win_amd64, packageBin) := {
  (file(s"target/classes/win/amd64/libzstd-jni-${version.value}.dll"), s"win/amd64/libzstd-jni-${version.value}.dll") :: classes
}
packageOptions in (Win_amd64, packageBin) ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "win_amd64"), packageBin in Win_amd64)
