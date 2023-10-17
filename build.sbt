val nameValue = "zstd-jni"

name := nameValue

version := {
  scala.io.Source.fromFile("version").getLines.next
}

scalaVersion := "2.13.12"

enablePlugins(JniPlugin, SbtOsgi, ModuleInfoPlugin)
moduleInfo := com.sandinh.javamodule.moduleinfo.JpmsModule(
  "com.github.luben.zstd_jni", // moduleName
  openModule = false,
)

autoScalaLibrary := false

crossPaths := false

Test / logBuffered := false

Test / parallelExecution := false

libraryDependencies ++= Seq(
  "org.scalatest"  %% "scalatest"  % "3.2.17" % "test",
  "org.scalatestplus" %% "scalacheck-1-17" % "3.2.17.0" % "test"
)

javacOptions ++= Seq("--release", "8", "-Xlint:unchecked")

doc / javacOptions := Seq("--release", "8")

// fork := true
// Check at runtime for JNI errors when running tests
Test / javaOptions ++= Seq("-Xcheck:jni")

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
  "com.github.luben.zstd.ZstdDirectBufferDecompressingStreamNoFinalizer",
  "com.github.luben.zstd.ZstdDirectBufferCompressingStreamNoFinalizer",
  "com.github.luben.zstd.ZstdBufferDecompressingStreamNoFinalizer"
)

jniLibSuffix := (System.getProperty("os.name").toLowerCase match {
  case os if os startsWith "mac"    => "dylib"
  case os if os startsWith "darwin" => "dylib"
  case os if os startsWith "win"    => "dll"
  case _                            => "so"
})

jniNativeCompiler := Option(System.getenv("CC")).getOrElse("gcc")

val PWD = Option(System.getenv("PWD")).getOrElse("./")

jniUseCpp11 := false

jniCppExtensions := Seq("c", "S")

jniGccFlags ++= Seq(
  "-std=c99", "-Wundef", "-Wshadow", "-Wcast-align", "-Wstrict-prototypes", "-Wno-unused-variable",
  "-Wpointer-arith", "-DZSTD_LEGACY_SUPPORT=4", "-DZSTD_MULTITHREAD=1", "-lpthread", "-flto"
)

// compilation on Windows with MSYS/gcc needs extra flags in order
// to produce correct DLLs, also it alway produces position independent
// code so let's remove the flag and silence a warning
jniGccFlags := (
  if (System.getProperty("os.name").toLowerCase startsWith "win")
    jniGccFlags.value.filterNot(_ == "-fPIC") ++
      Seq("-D_JNI_IMPLEMENTATION_", "-Wl,--kill-at",
        "-static-libgcc", "-Wl,--version-script=" + PWD + "/libzstd-jni.so.map")
  else if (System.getProperty("os.name").toLowerCase startsWith "mac")
    // MacOS uses clang that does not support the "-static-libgcc" and version scripts,
    // but visibility can be modified by `-exported_symbols_list`
    jniGccFlags.value ++ Seq("-exported_symbols_list", PWD + "/libzstd-jni.so.exported")
  else
    // the default is compilation with GCC
    jniGccFlags.value ++ Seq(
        "-static-libgcc", "-Wl,--version-script=" + PWD + "/libzstd-jni.so.map")
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
  (Compile / target).value / "classes" / os / arch
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

Test / publishArtifact := false

pomIncludeRepository := { _ => false }

organization := "com.github.luben"

licenses := Seq("BSD 2-Clause License" -> url("https://opensource.org/licenses/BSD-2-Clause"))

description := "JNI bindings for Zstd native library that provides fast and high " +
                "compression lossless algorithm for Java and all JVM languages."

Compile / packageBin / packageOptions ++= Seq(
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
      |linux/riscv64/libzstd-jni-${version.value}.so;osname=Linux;processor=riscv64,
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
    "darwin.aarch64", "win.amd64", "win.x86", "freebsd.amd64", "freebsd.i386",
    "linux.riscv64"
)
// Explicitly specify the version of JavaSE required
// (rather depend on figuring that out from the JDK it was built with)
OsgiKeys.requireCapability := "osgi.ee;filter:=\"(&(osgi.ee=JavaSE)(version>=1.8))\""

// Jacoco coverage setting
jacocoReportSettings := JacocoReportSettings(
  "Jacoco Coverage Report",
  None,
  JacocoThresholds(),
  Seq(JacocoReportFormats.XML, JacocoReportFormats.HTML),
  "utf-8")
jacocoInstrumentationExcludes := Seq("module-info")

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
Linux_amd64 / packageBin / mappings := {
  (file(s"target/classes/linux/amd64/libzstd-jni-${version.value}.so"), s"linux/amd64/libzstd-jni-${version.value}.so") :: classes
}
Linux_amd64 / packageBin / packageOptions ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "linux_amd64"), Linux_amd64 / packageBin)

lazy val Linux_i386 = config("linux_i386").extend(Compile)
inConfig(Linux_i386)(Defaults.compileSettings)
Linux_i386 / packageBin / mappings := {
  (file(s"target/classes/linux/i386/libzstd-jni-${version.value}.so"), s"linux/i386/libzstd-jni-${version.value}.so") :: classes
}
Linux_i386 / packageBin / packageOptions ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "linux_i386"), Linux_i386 / packageBin)

lazy val Linux_aarch64 = config("linux_aarch64").extend(Compile)
inConfig(Linux_aarch64)(Defaults.compileSettings)
Linux_aarch64 / packageBin / mappings := {
  (file(s"target/classes/linux/aarch64/libzstd-jni-${version.value}.so"), s"linux/aarch64/libzstd-jni-${version.value}.so") :: classes
}
Linux_aarch64 / packageBin / packageOptions ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "linux_aarch64"), Linux_aarch64 / packageBin)

lazy val Linux_arm = config("linux_arm").extend(Compile)
inConfig(Linux_arm)(Defaults.compileSettings)
Linux_arm / packageBin / mappings := {
  (file(s"target/classes/linux/arm/libzstd-jni-${version.value}.so"), s"linux/arm/libzstd-jni-${version.value}.so") :: classes
}
Linux_arm / packageBin / packageOptions ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "linux_arm"), Linux_arm / packageBin)

lazy val Linux_ppc64le = config("linux_ppc64le").extend(Compile)
inConfig(Linux_ppc64le)(Defaults.compileSettings)
Linux_ppc64le / packageBin / mappings := {
  (file(s"target/classes/linux/ppc64le/libzstd-jni-${version.value}.so"), s"linux/ppc64le/libzstd-jni-${version.value}.so") :: classes
}
Linux_ppc64le / packageBin / packageOptions ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "linux_ppc64le"), Linux_ppc64le / packageBin)

lazy val Linux_ppc64 = config("linux_ppc64").extend(Compile)
inConfig(Linux_ppc64)(Defaults.compileSettings)
Linux_ppc64 / packageBin / mappings := {
  (file(s"target/classes/linux/ppc64/libzstd-jni-${version.value}.so"), s"linux/ppc64/libzstd-jni-${version.value}.so") :: classes
}
Linux_ppc64 / packageBin / packageOptions ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "linux_ppc64"), Linux_ppc64 / packageBin)

lazy val Linux_mips64 = config("linux_mips64").extend(Compile)
inConfig(Linux_mips64)(Defaults.compileSettings)
Linux_mips64 / packageBin / mappings := {
  (file(s"target/classes/linux/mips64/libzstd-jni-${version.value}.so"), s"linux/mips64/libzstd-jni-${version.value}.so") :: classes
}
Linux_mips64 / packageBin / packageOptions ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "linux_mips64"), Linux_mips64 / packageBin)

lazy val Linux_loongarch64 = config("linux_loongarch64").extend(Compile)
inConfig(Linux_loongarch64)(Defaults.compileSettings)
Linux_loongarch64 / packageBin / mappings := {
  (file(s"target/classes/linux/loongarch64/libzstd-jni-${version.value}.so"), s"linux/loongarch64/libzstd-jni-${version.value}.so") :: classes
}
Linux_loongarch64 / packageBin / packageOptions ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "linux_loongarch64"), Linux_loongarch64 / packageBin)

lazy val Linux_s390x = config("linux_s390x").extend(Compile)
inConfig(Linux_s390x)(Defaults.compileSettings)
Linux_s390x / packageBin / mappings := {
  (file(s"target/classes/linux/s390x/libzstd-jni-${version.value}.so"), s"linux/s390x/libzstd-jni-${version.value}.so") :: classes
}
Linux_s390x / packageBin / packageOptions ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "linux_s390x"), Linux_s390x / packageBin)

lazy val Linux_riscv64 = config("linux_riscv64").extend(Compile)
inConfig(Linux_riscv64)(Defaults.compileSettings)
Linux_riscv64 / packageBin / mappings := {
  (file(s"target/classes/linux/riscv64/libzstd-jni-${version.value}.so"), s"linux/riscv64/libzstd-jni-${version.value}.so") :: classes
}
Linux_riscv64 / packageBin / packageOptions ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "linux_riscv64"), Linux_riscv64 / packageBin)

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
Darwin_x86_64 / packageBin / mappings := {
  (file(s"target/classes/darwin/x86_64/libzstd-jni-${version.value}.dylib"), s"darwin/x86_64/libzstd-jni-${version.value}.dylib") :: classes
}
Darwin_x86_64 / packageBin / packageOptions ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "darwin_x86_64"), Darwin_x86_64 / packageBin)

lazy val Darwin_aarch64 = config("darwin_aarch64").extend(Compile)
inConfig(Darwin_aarch64)(Defaults.compileSettings)
Darwin_aarch64 / packageBin / mappings := {
  (file(s"target/classes/darwin/aarch64/libzstd-jni-${version.value}.dylib"), s"darwin/aarch64/libzstd-jni-${version.value}.dylib") :: classes
}
Darwin_aarch64 / packageBin / packageOptions ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "darwin_aarch64"), Darwin_aarch64 / packageBin)

lazy val FreeBSD_amd64 = config("freebsd_amd64").extend(Compile)
inConfig(FreeBSD_amd64)(Defaults.compileSettings)
FreeBSD_amd64 / packageBin / mappings := {
  (file(s"target/classes/freebsd/amd64/libzstd-jni-${version.value}.so"), s"freebsd/amd64/libzstd-jni-${version.value}.so") :: classes
}
FreeBSD_amd64 / packageBin / packageOptions ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "freebsd_amd64"), FreeBSD_amd64 / packageBin)

lazy val FreeBSD_i386 = config("freebsd_i386").extend(Compile)
inConfig(FreeBSD_i386)(Defaults.compileSettings)
FreeBSD_i386 / packageBin / mappings := {
  (file(s"target/classes/freebsd/i386/libzstd-jni-${version.value}.so"), s"freebsd/i386/libzstd-jni-${version.value}.so") :: classes
}
FreeBSD_i386 / packageBin / packageOptions ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "freebsd_i386"), FreeBSD_i386 / packageBin)

val Win_x86 = config("win_x86").extend(Compile)
inConfig(Win_x86)(Defaults.compileSettings)
Win_x86 / packageBin / mappings := {
  (file(s"target/classes/win/x86/libzstd-jni-${version.value}.dll"), s"win/x86/libzstd-jni-${version.value}.dll") :: classes
}
Win_x86 / packageBin / packageOptions ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "win_x86"), Win_x86 / packageBin)

val Win_amd64 = config("win_amd64").extend(Compile)
inConfig(Win_amd64)(Defaults.compileSettings)
Win_amd64 / packageBin / mappings := {
  (file(s"target/classes/win/amd64/libzstd-jni-${version.value}.dll"), s"win/amd64/libzstd-jni-${version.value}.dll") :: classes
}
Win_amd64 / packageBin / packageOptions ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "win_amd64"), Win_amd64 / packageBin)
