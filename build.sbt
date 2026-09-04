val nameValue = "zstd-jni"

name := nameValue

version := {
  scala.io.Source.fromFile("version").getLines.next
}

ThisBuild / versionScheme := Some("pvp")

scalaVersion := "2.13.12"

enablePlugins(JniPlugin, ModuleInfoPlugin)
moduleInfo := com.sandinh.javamodule.moduleinfo.JpmsModule(
  "com.github.luben.zstd_jni", // moduleName
  openModule = false,
)

autoScalaLibrary := false

crossPaths := false

Test / logBuffered := false

Test / parallelExecution := false

libraryDependencies ++= Seq(
  "org.jetbrains" % "annotations" % "24.1.0" % "provided",
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
  "-Wpointer-arith", "-DZSTD_MULTITHREAD=1", "-lpthread", "-flto"
)

// compilation on Windows with MSYS/gcc needs extra flags in order
// to produce correct DLLs, also it alway produces position independent
// code so let's remove the flag and silence a warning
jniGccFlags := (
  if (System.getProperty("os.name").toLowerCase startsWith "win")
    jniGccFlags.value.filterNot(_ == "-fPIC") ++
      Seq("-D_JNI_IMPLEMENTATION_", "-Wl,--kill-at",
        // Exports ZSTD_* for FFM's symbol lookups. jni_md.h's JNIEXPORT dllexport turns
        // ld's auto-export off, and on PE the version script can only filter, not add.
        "-DZSTD_DLL_EXPORT=1",
        "-static-libgcc", "-Wl,--version-script=" + PWD + "/libzstd-jni.so.map")
  else if (System.getProperty("os.name").toLowerCase startsWith "mac") {
   // For intel, target the latest version that supported 32bit binaries
    val target = if (System.getProperty("os.arch") == "x86_64") {
      Seq("-target","x86_64-apple-macos10.14", "-mmacosx-version-min=10.14")
    } else {
      Seq("-target","arm64-apple-macos11",     "-mmacosx-version-min=11")
    }
    // MacOS uses clang that does not support the "-static-libgcc" and version scripts,
    // but visibility can be modified by `-exported_symbols_list`
    jniGccFlags.value ++ Seq("-exported_symbols_list", PWD + "/libzstd-jni.so.exported") ++ target
  } else
    // the default is compilation with GCC
    jniGccFlags.value ++ Seq(
        "-static-libgcc", "-Wl,--version-script=" + PWD + "/libzstd-jni.so.map", "-Wl,-Bsymbolic", "-Wl,-z,relro,-z,now")
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

// Multi-Release dispatch is a jar feature, so `test` only ever runs the JNI classes: a
// directory root resolves com/github/luben/zstd/Foo.class, never the META-INF copy next
// to it. The jar is where the FFM classes get tested (`testFromJar`) and validated
// (`verifyMultiRelease`); compiling them here just fails a broken source locally.
// (Off `Test / test`, not `compile` - `ffmCompile` already depends on `Compile / compile`.)
Test / test := (Test / test).dependsOn(ffmCompile).value

// The FFM sources are not in unmanagedSourceDirectories - the base compile is
// `--release 8` and would reject java.lang.foreign - so the sources jar gets them here,
// under META-INF/versions to mirror the class layout and avoid colliding at the root.
Compile / packageSrc / mappings ++= {
  val dir = ffmSourceDir.value
  (dir ** "*.java").get.pair(Path.relativeTo(dir)).map { case (f, rel) =>
    f -> s"META-INF/versions/$ffmRelease/${rel.replace(java.io.File.separatorChar, '/')}"
  }
}

// Sonatype
import xerial.sbt.Sonatype.sonatypeCentralHost
ThisBuild / sonatypeCredentialHost := sonatypeCentralHost
publishTo := sonatypePublishToBundle.value

publishMavenStyle := true

Test / publishArtifact := false

pomIncludeRepository := { _ => false }

organization := "com.github.luben"

licenses := Seq("BSD 2-Clause License" -> url("https://opensource.org/licenses/BSD-2-Clause"))

description := "JNI bindings for Zstd native library that provides fast and high " +
                "compression lossless algorithm for Java and all JVM languages."

// Without this the JVM ignores META-INF/versions/ entirely and every runtime
// gets the JNI implementation from the jar root.
val multiReleaseAttribute =
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Multi-Release") -> "true")

Compile / packageBin / packageOptions ++= Seq(
  multiReleaseAttribute,
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Bnd-LastModified") -> s"${java.lang.System.currentTimeMillis()}"),
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Bundle-Name") -> "zstd-jni"),
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Bundle-SymbolicName") -> "com.github.luben.zstd-jni"),
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Bundle-Description") -> description.value),
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Bundle-Vendor") -> organization.value),
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Bundle-License") -> "https://opensource.org/licenses/BSD-2-Clause;description=BSD 2-Clause License"),
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Bundle-Version") -> version.value.replace("-", ".")),
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Bundle-ManifestVersion") -> "2"),
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Bundle-NativeCode") ->
   s"""darwin/x86_64/libzstd-jni-${version.value}.dylib;osname=MacOS;osname=MacOSX;processor=x86_64,
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
      |win/aarch64/libzstd-jni-${version.value}.dll;osname=Win32;processor=aarch64,
      |win/x86/libzstd-jni-${version.value}.dll;osname=Win32;processor=x86,
      |aix/ppc64/libzstd-jni-${version.value}.so;osname=AIX;processor=ppc64""".stripMargin.replace("\n", "")),
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Export-Package") -> s"""com.github.luben.zstd;version="${version.value.replace("-", ".")}",com.github.luben.zstd.util;version="${version.value.replace("-", ".")}""""),
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Import-Package") -> "org.osgi.framework;resolution:=optional"),
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Private-Package") ->
    """darwin.aarch64,
      |darwin.x86_64,
      |freebsd.amd64,
      |freebsd.i386,
      |linux.aarch64,
      |linux.amd64,
      |linux.arm,
      |linux.i386,
      |linux.loongarch64,
      |linux.mips64,
      |linux.ppc64,
      |linux.ppc64le,
      |linux.riscv64,
      |linux.s390x,
      |win.aarch64,
      |win.amd64,
      |win.x86,
      |aix.ppc64""".stripMargin.replace("\n","")),
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Require-Capability") -> """osgi.ee;filter:="(&(osgi.ee=JavaSE)(version>=1.8))""""),
)

// publish the main jar as bundle
Compile / packageBin / artifact := {
  val prev: Artifact = (Compile / packageBin / artifact).value
  prev.withType("bundle")
}

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
  Process("./gradlew",  "assembleRelease" :: Nil).!
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
addArtifact(Artifact(nameValue, "linux_amd64"), Linux_amd64 / packageBin)

lazy val Linux_i386 = config("linux_i386").extend(Compile)
inConfig(Linux_i386)(Defaults.compileSettings)
Linux_i386 / packageBin / mappings := {
  (file(s"target/classes/linux/i386/libzstd-jni-${version.value}.so"), s"linux/i386/libzstd-jni-${version.value}.so") :: classes
}
addArtifact(Artifact(nameValue, "linux_i386"), Linux_i386 / packageBin)

lazy val Linux_aarch64 = config("linux_aarch64").extend(Compile)
inConfig(Linux_aarch64)(Defaults.compileSettings)
Linux_aarch64 / packageBin / mappings := {
  (file(s"target/classes/linux/aarch64/libzstd-jni-${version.value}.so"), s"linux/aarch64/libzstd-jni-${version.value}.so") :: classes
}
addArtifact(Artifact(nameValue, "linux_aarch64"), Linux_aarch64 / packageBin)

lazy val Linux_arm = config("linux_arm").extend(Compile)
inConfig(Linux_arm)(Defaults.compileSettings)
Linux_arm / packageBin / mappings := {
  (file(s"target/classes/linux/arm/libzstd-jni-${version.value}.so"), s"linux/arm/libzstd-jni-${version.value}.so") :: classes
}
addArtifact(Artifact(nameValue, "linux_arm"), Linux_arm / packageBin)

lazy val Linux_ppc64le = config("linux_ppc64le").extend(Compile)
inConfig(Linux_ppc64le)(Defaults.compileSettings)
Linux_ppc64le / packageBin / mappings := {
  (file(s"target/classes/linux/ppc64le/libzstd-jni-${version.value}.so"), s"linux/ppc64le/libzstd-jni-${version.value}.so") :: classes
}
addArtifact(Artifact(nameValue, "linux_ppc64le"), Linux_ppc64le / packageBin)

lazy val Linux_ppc64 = config("linux_ppc64").extend(Compile)
inConfig(Linux_ppc64)(Defaults.compileSettings)
Linux_ppc64 / packageBin / mappings := {
  (file(s"target/classes/linux/ppc64/libzstd-jni-${version.value}.so"), s"linux/ppc64/libzstd-jni-${version.value}.so") :: classes
}
addArtifact(Artifact(nameValue, "linux_ppc64"), Linux_ppc64 / packageBin)

lazy val Linux_mips64 = config("linux_mips64").extend(Compile)
inConfig(Linux_mips64)(Defaults.compileSettings)
Linux_mips64 / packageBin / mappings := {
  (file(s"target/classes/linux/mips64/libzstd-jni-${version.value}.so"), s"linux/mips64/libzstd-jni-${version.value}.so") :: classes
}
addArtifact(Artifact(nameValue, "linux_mips64"), Linux_mips64 / packageBin)

lazy val Linux_loongarch64 = config("linux_loongarch64").extend(Compile)
inConfig(Linux_loongarch64)(Defaults.compileSettings)
Linux_loongarch64 / packageBin / mappings := {
  (file(s"target/classes/linux/loongarch64/libzstd-jni-${version.value}.so"), s"linux/loongarch64/libzstd-jni-${version.value}.so") :: classes
}
addArtifact(Artifact(nameValue, "linux_loongarch64"), Linux_loongarch64 / packageBin)

lazy val Linux_s390x = config("linux_s390x").extend(Compile)
inConfig(Linux_s390x)(Defaults.compileSettings)
Linux_s390x / packageBin / mappings := {
  (file(s"target/classes/linux/s390x/libzstd-jni-${version.value}.so"), s"linux/s390x/libzstd-jni-${version.value}.so") :: classes
}
addArtifact(Artifact(nameValue, "linux_s390x"), Linux_s390x / packageBin)

lazy val Linux_riscv64 = config("linux_riscv64").extend(Compile)
inConfig(Linux_riscv64)(Defaults.compileSettings)
Linux_riscv64 / packageBin / mappings := {
  (file(s"target/classes/linux/riscv64/libzstd-jni-${version.value}.so"), s"linux/riscv64/libzstd-jni-${version.value}.so") :: classes
}
addArtifact(Artifact(nameValue, "linux_riscv64"), Linux_riscv64 / packageBin)

lazy val Aix_ppc64 = config("aix_ppc64").extend(Compile)
inConfig(Aix_ppc64)(Defaults.compileSettings)
Aix_ppc64 / packageBin / mappings := {
  (file(s"target/classes/aix/ppc64/libzstd-jni-${version.value}.so"), s"aix/ppc64/libzstd-jni-${version.value}.so") :: classes
}
addArtifact(Artifact(nameValue, "aix_ppc64"), Aix_ppc64 / packageBin)

lazy val Darwin_x86_64 = config("darwin_x86_64").extend(Compile)
inConfig(Darwin_x86_64)(Defaults.compileSettings)
Darwin_x86_64 / packageBin / mappings := {
  (file(s"target/classes/darwin/x86_64/libzstd-jni-${version.value}.dylib"), s"darwin/x86_64/libzstd-jni-${version.value}.dylib") :: classes
}
addArtifact(Artifact(nameValue, "darwin_x86_64"), Darwin_x86_64 / packageBin)

lazy val Darwin_aarch64 = config("darwin_aarch64").extend(Compile)
inConfig(Darwin_aarch64)(Defaults.compileSettings)
Darwin_aarch64 / packageBin / mappings := {
  (file(s"target/classes/darwin/aarch64/libzstd-jni-${version.value}.dylib"), s"darwin/aarch64/libzstd-jni-${version.value}.dylib") :: classes
}
addArtifact(Artifact(nameValue, "darwin_aarch64"), Darwin_aarch64 / packageBin)

lazy val FreeBSD_amd64 = config("freebsd_amd64").extend(Compile)
inConfig(FreeBSD_amd64)(Defaults.compileSettings)
FreeBSD_amd64 / packageBin / mappings := {
  (file(s"target/classes/freebsd/amd64/libzstd-jni-${version.value}.so"), s"freebsd/amd64/libzstd-jni-${version.value}.so") :: classes
}
addArtifact(Artifact(nameValue, "freebsd_amd64"), FreeBSD_amd64 / packageBin)

lazy val FreeBSD_i386 = config("freebsd_i386").extend(Compile)
inConfig(FreeBSD_i386)(Defaults.compileSettings)
FreeBSD_i386 / packageBin / mappings := {
  (file(s"target/classes/freebsd/i386/libzstd-jni-${version.value}.so"), s"freebsd/i386/libzstd-jni-${version.value}.so") :: classes
}
addArtifact(Artifact(nameValue, "freebsd_i386"), FreeBSD_i386 / packageBin)

lazy val Win_x86 = config("win_x86").extend(Compile)
inConfig(Win_x86)(Defaults.compileSettings)
Win_x86 / packageBin / mappings := {
  (file(s"target/classes/win/x86/libzstd-jni-${version.value}.dll"), s"win/x86/libzstd-jni-${version.value}.dll") :: classes
}
addArtifact(Artifact(nameValue, "win_x86"), Win_x86 / packageBin)

lazy val Win_amd64 = config("win_amd64").extend(Compile)
inConfig(Win_amd64)(Defaults.compileSettings)
Win_amd64 / packageBin / mappings := {
  (file(s"target/classes/win/amd64/libzstd-jni-${version.value}.dll"), s"win/amd64/libzstd-jni-${version.value}.dll") :: classes
}
addArtifact(Artifact(nameValue, "win_amd64"), Win_amd64 / packageBin)

lazy val Win_aarch64 = config("win_aarch64").extend(Compile)
inConfig(Win_aarch64)(Defaults.compileSettings)
Win_aarch64 / packageBin / mappings := {
  (file(s"target/classes/win/aarch64/libzstd-jni-${version.value}.dll"), s"win/aarch64/libzstd-jni-${version.value}.dll") :: classes
}
addArtifact(Artifact(nameValue, "win_aarch64"), Win_aarch64 / packageBin)

lazy val Cloud = config("cloud").extend(Compile)
inConfig(Cloud)(Defaults.compileSettings)
Cloud / packageBin / mappings := {
  (file(s"target/classes/linux/amd64/libzstd-jni-${version.value}.so"), s"linux/amd64/libzstd-jni-${version.value}.so") ::
  (file(s"target/classes/linux/aarch64/libzstd-jni-${version.value}.so"), s"linux/aarch64/libzstd-jni-${version.value}.so") ::
  (file(s"target/classes/darwin/aarch64/libzstd-jni-${version.value}.dylib"), s"darwin/aarch64/libzstd-jni-${version.value}.dylib") ::
  classes
}
addArtifact(Artifact(nameValue, "cloud"), Cloud / packageBin)

val classifiedConfigs = Seq(
  Linux_amd64, Linux_i386, Linux_aarch64, Linux_arm, Linux_ppc64le, Linux_ppc64,
  Linux_mips64, Linux_loongarch64, Linux_s390x, Linux_riscv64, Aix_ppc64,
  Darwin_x86_64, Darwin_aarch64, FreeBSD_amd64, FreeBSD_i386,
  Win_x86, Win_amd64, Win_aarch64, Cloud
)

// jniCompile (the .so at <os>/<arch>/) and ffmCompile (META-INF/versions/22) write into
// target/classes without going through `compile`. `mappings` is what reads that directory
// and, as a *sibling* of packageBin, is free to run first - and sbt drops a mapping whose
// source file is missing without a word, so the jar comes out quietly incomplete. Hence the
// edge on `mappings`, not `packageBin`. The classified configs need it too: their `classes`
// list is a lazy val snapshotting target/classes the first time any config forces it.
(Compile +: classifiedConfigs).flatMap(c => Seq(
  c / packageBin / mappings := (c / packageBin / mappings).dependsOn(jniCompile, ffmCompile).value,
  c / packageBin := {
    val jar = (c / packageBin).value
    verifyMultiRelease(jar, streams.value.log)
    jar
  }
)) ++
  // Each classified config gets its own packageOptions from Defaults.compileSettings, so it
  // never delegates to the Compile-scoped manifest above - Multi-Release goes on each one.
  classifiedConfigs.map(c => c / packageBin / packageOptions += multiReleaseAttribute)

// So CI can multi-release-check every published jar, not just the main one. Not
// `packagedArtifacts`, which would also run aarTask and need the Android SDK.
lazy val packageClassified = taskKey[Seq[File]]("Package every classified jar")
packageClassified := packageBin.all(
  ScopeFilter(configurations = inConfigurations(classifiedConfigs: _*))
).value

// =========== FFM API SUPPORT ===========

// Multi-Release JAR: on JDK 22+ the classes in META-INF/versions/22 win over their
// same-named copies in the jar root. 22 is where the FFM API was finalized (JEP 454);
// these sources compile with `--release 22`, the rest of the project with 8.
val ffmRelease = "22"

lazy val ffmSourceDir = settingKey[File]("Source directory of the JDK 22+ (FFM) implementation")
lazy val ffmClassDir  = settingKey[File]("Where the versioned classes are written, inside the jar content root")
lazy val ffmCompile   = taskKey[Seq[File]](s"Compile the FFM sources into META-INF/versions/$ffmRelease")

ffmSourceDir := (Compile / sourceDirectory).value / s"java$ffmRelease"

// Inside Compile/classDirectory because that directory *is* the jar content root, so
// the versioned classes need no extra mappings - as jniBinPath does for the .so. On a
// classpath they stay inert: a directory entry never resolves the META-INF copy.
ffmClassDir := (Compile / classDirectory).value / "META-INF" / "versions" / ffmRelease

ffmCompile := {
  val log     = streams.value.log
  val out     = ffmClassDir.value
  val sources = (ffmSourceDir.value ** "*.java").get
  // The FFM sources reference BufferPool, Zstd, ... so force the base compile first.
  val baseClasses = (Compile / classDirectory).value
  val _           = (Compile / compile).value
  // Hoisted: sbt evaluates task dependencies regardless of the branch taken.
  val depCp       = (Compile / dependencyClasspath).value.files

  if (sources.isEmpty) {
    IO.delete(out)
    Seq.empty[File]
  } else if (!scala.util.Properties.isJavaAtLeast(ffmRelease)) {
    // windows-x86 is the one job still on JDK 11 (JEP 479). Skipping keeps it green;
    // verifyMultiRelease makes a silently-empty versions/22 fail at package time.
    log.warn(s"JDK $ffmRelease+ is required for the FFM sources, but this is JDK " +
      s"${sys.props.getOrElse("java.version", "?")} - the jar will carry no " +
      s"META-INF/versions/$ffmRelease and every runtime will get the JNI classes.")
    IO.delete(out)
    Seq.empty[File]
  } else {
    IO.delete(out)
    IO.createDirectory(out)
    val cp = (depCp :+ baseClasses).mkString(java.io.File.pathSeparator)
    val args = Seq(
      "--release", ffmRelease,
      "-Xlint:unchecked",
      "-classpath", cp,
      "-d", out.getAbsolutePath
    ) ++ sources.map(_.getAbsolutePath)
    log.info(s"Compiling ${sources.size} FFM sources to $out ...")
    val compiler = javax.tools.ToolProvider.getSystemJavaCompiler
    if (compiler == null) sys.error("No system Java compiler available (a JRE rather than a JDK?)")
    val err = new java.io.ByteArrayOutputStream
    val rc  = compiler.run(null, null, err, args: _*)
    val msg = err.toString("UTF-8")
    if (msg.nonEmpty) log.info(msg)
    if (rc != 0) sys.error(s"Compilation of the FFM sources failed (javac exit code $rc)")
    (out ** "*.class").get
  }
}

// Jacoco walks Compile/classDirectory as a directory tree, not a classpath entry, so it
// sees the META-INF/versions/<n> copies too, and two same-named classes in one
// CoverageBuilder fails with "Can't add different class with same name". Filters match the
// path relative to classDirectory with separators as dots. Right on the merits too: `sbt
// jacoco` exercises the JNI path, so those copies would only add 0%-covered duplicates.
// (Here rather than with the other jacoco settings: reading ffmRelease above its definition
// would give "META-INF.versions.null.*" - a forward reference is null, not an error.)
val versionedClasses = s"META-INF.versions.$ffmRelease.*"
jacocoExcludes := Seq(versionedClasses)
jacocoInstrumentationExcludes := Seq("module-info", versionedClasses)

// It takes nothing from the compile graph - jar path and test classes are settings, the
// dependency jars come from `update` - because sbt caches task results per command, so an
// edge to `packageBin` or `Test / fullClasspath` would re-run jniCompile, a full gcc+LTO
// build of libzstd, once per run. `testFromJarSetup` does that once. It re-checks the jar
// with verifyMultiRelease so a green FFM run cannot mean "resolved to the base class because
// there was nothing else". Java home defaults to JAVA_HOME, then to the JDK running sbt.
lazy val testFromJarSetup = taskKey[Unit]("Package the jar and compile the tests, ready for testFromJar")

// Runs the suite against the packaged jar, the only thing that exercises Multi-Release
// dispatch the way a consumer does: which implementation answers is decided by the feature
// version of the JVM passed in and by nothing else.
//
//   testFromJar <jdk22+>                                          -> FFM
//   testFromJar <jdk22+> -Djdk.util.jar.enableMultiRelease=false  -> JNI
//   testFromJar <jdk8-21>                                         -> JNI
//
// Same artifact every time, only the runtime differs. It forks its own JVM because sbt runs
// `Test / test` in-process, pinned to whatever JDK built the jar.
lazy val testFromJar = inputKey[Unit]("Run the test suite against the packaged jar under the given JDK")

// One task rather than two commands: sbt would rebuild the native library for each
// of them, and this way the whole graph - jniCompile included - runs once.
testFromJarSetup := {
  val _  = (Compile / packageBin).value
  // `products`, not `compile`: it also copies the test resources into place.
  val __ = (Test / products).value
}

testFromJar := {
  val log  = streams.value.log
  val args = sbt.complete.DefaultParsers.spaceDelimited("[java-home] [jvm-opts...]").parsed

  val jar         = (Compile / packageBin / artifactPath).value
  val testClasses = (Test / classDirectory).value
  if (!jar.isFile || (testClasses ** "*.class").get.isEmpty)
    sys.error(s"$jar or $testClasses is missing - run `sbt testFromJarSetup` first")
  verifyMultiRelease(jar, log)

  // The dependency jars plus the test classes, and no other product directory: the point is
  // that com.github.luben.zstd comes out of the jar now. Taking the external classpath rather
  // than filtering the full one also sidesteps sbt-jacoco, whose Test/fullClasspath swaps
  // target/classes for target/jacoco/instrumented-classes and would put the JNI
  // implementation back in front of the jar - green, and meaningless.
  //
  // The test classes must be on -cp, not only on scalatest's -R runpath: the suite is itself
  // in package com.github.luben.zstd and calls package-private members, and from the runpath
  // it lands in a different runtime package from the jar's copy - IllegalAccessError.
  val entries = (testClasses +: (Test / externalDependencyClasspath).value.files).map(_.getCanonicalFile)

  // Backstop, asked semantically rather than by path shape.
  val leaked = entries.filter(e => (e / "com" / "github" / "luben" / "zstd" / "Zstd.class").isFile)
  if (leaked.nonEmpty)
    sys.error("these classpath entries would shadow the jar's own classes:\n" +
              leaked.map("  " + _).mkString("\n"))

  // A leading non-flag argument is the java home.
  val (homeArg, jvmOpts) = args.toList match {
    case h :: rest if !h.startsWith("-") => (h, rest)
    case opts                            => (sys.env.getOrElse("JAVA_HOME", sys.props("java.home")), opts)
  }
  val javaHome = file(homeArg)
  val javaBin  = Seq("java", "java.exe").map(javaHome / "bin" / _).find(_.canExecute)
    .getOrElse(sys.error(s"no java executable under $javaHome"))
  val release  = featureVersion(javaBin)

  val cmd =
    javaBin.getPath ::
    // JEP 472: restricted methods warn on 24 and will be blocked later. Both paths need it -
    // System.load for JNI, the downcalls for FFM - and JDKs below 22 reject the flag.
    (if (release >= 22) List("--enable-native-access=ALL-UNNAMED") else Nil) :::
    jvmOpts :::
    // The jar goes first; the base classes are already out of what follows.
    "-cp" :: (jar +: entries).map(_.getPath).mkString(java.io.File.pathSeparator) ::
    "org.scalatest.tools.Runner" :: "-R" :: testClasses.getPath :: "-o" :: Nil

  log.info(s"Running the suite on JDK $release against ${jar.getName}" +
           (if (jvmOpts.isEmpty) "" else jvmOpts.mkString(" with ", " ", "")))

  import scala.sys.process._
  val rc = Process(cmd).!
  if (rc != 0) sys.error(s"the suite failed on JDK $release (exit code $rc)")
}

// Asks the JVM itself rather than guessing from the path or from a release file.
def featureVersion(javaBin: File): Int = {
  import scala.sys.process._
  val out = new StringBuilder
  // -XshowSettings writes to stderr, so both streams go to the same buffer.
  val rc = Process(Seq(javaBin.getPath, "-XshowSettings:properties", "-version")) !
    ProcessLogger(line => out.append(line).append('\n'))
  if (rc != 0) sys.error(s"$javaBin exited $rc:\n$out")
  val value = out.toString.linesIterator.map(_.trim)
    .collectFirst { case l if l.startsWith("java.specification.version") => l.substring(l.indexOf('=') + 1).trim }
    .getOrElse(sys.error(s"no java.specification.version in the output of $javaBin:\n$out"))
  // "1.8" on 8, "11" / "25" from 9 onwards.
  if (value.startsWith("1.")) value.substring(2).toInt else value.toInt
}

// Three questions about the packaged jar, none of which a test run can answer - a jar
// with no versioned classes resolves to the JNI copies and passes every test green:
//
//   1. META-INF/versions/22 is not empty              entries()
//   2. a JDK 22 loads those, not the jar-root copy    getJarEntry().getRealName()
//   3. their public API matches that jar-root copy    javap --multi-release 8 vs 22
//
// (2) is the JDK's own resolution, manifest attribute included - the same question a
// consumer's class loader asks. Runs from packageBin, so every jar this build writes
// is checked, published or not.
def verifyMultiRelease(jar: File, log: Logger): Unit = {
  import scala.collection.JavaConverters._
  val prefix      = s"META-INF/versions/$ffmRelease/"
  val baseRelease = "8"

  // Below 22 `ffmCompile` skips and says so, so an empty jar is expected rather
  // than broken. Everything that publishes builds on a current JDK.
  if (!scala.util.Properties.isJavaAtLeast(ffmRelease)) {
    log.warn(s"${jar.getName}: built on JDK ${sys.props.getOrElse("java.version", "?")}, " +
      s"so it carries no META-INF/versions/$ffmRelease and every runtime will get the JNI classes.")
    return
  }

  val javap = java.util.spi.ToolProvider.findFirst("javap")
    .orElseThrow(() => new RuntimeException("javap tool not available"))

  // javap prints the type declaration first, then one line per public member: the
  // members are the API, the declaration says whether the type itself is public.
  // --multi-release picks which copy to read, so both come out of the same jar.
  def javapLines(release: String, fqcn: String): Seq[String] = {
    val buf = new java.io.StringWriter
    val pw  = new java.io.PrintWriter(buf)
    val rc  = javap.run(pw, pw, "-public", "--multi-release", release, "-cp", jar.getAbsolutePath, fqcn)
    pw.flush()
    if (rc != 0)
      sys.error(s"javap failed on $fqcn at release $release in ${jar.getName} (exit code $rc):\n$buf")
    buf.toString.linesIterator
      .map(_.trim)
      .filter(_.nonEmpty)
      .filterNot(_.startsWith("Compiled from"))
      .toSeq
  }

  // `native` is normalised away - dropping it is precisely what this port does, and
  // not a binary-compatibility change. Sorting compares the set of members, not
  // their declaration order.
  def api(release: String, fqcn: String): Seq[String] =
    javapLines(release, fqcn).map(_.replace("native ", "")).sorted

  def fqcnOf(entry: String): String = entry.stripSuffix(".class").replace('/', '.')

  val jf = new java.util.jar.JarFile(
    jar, true, java.util.zip.ZipFile.OPEN_READ, java.lang.Runtime.Version.parse(ffmRelease))
  try {
    // entries(), not getEntry: this handle resolves, so getEntry would report every
    // versioned class as having a base copy. module-info is the JDK's own business.
    val classes   = jf.entries().asScala.map(_.getName)
      .filter(n => n.endsWith(".class") && !n.endsWith("module-info.class")).toList
    val versioned = classes.filter(_.startsWith(prefix)).map(_.stripPrefix(prefix)).sorted
    val baseNames = classes.filterNot(_.startsWith("META-INF/versions/")).toSet

    if (versioned.isEmpty)
      sys.error(s"${jar.getName} has no $prefix*.class entries, so JDK $ffmRelease+ would load the " +
        "JNI implementation from the jar root. Was it packaged before ffmCompile ran?")

    val misdispatched = versioned.flatMap { name =>
      val got = Option(jf.getJarEntry(name)).map(_.getRealName)
      if (got.contains(prefix + name)) None
      else Some(s"  $name resolves to ${got.getOrElse("<absent>")}, expected $prefix$name")
    }

    if (misdispatched.nonEmpty)
      sys.error(s"${jar.getName} does not dispatch as a Multi-Release JAR - is `Multi-Release: true` " +
        s"in its manifest?\n" + misdispatched.mkString("\n"))

    // Versioned-only classes (helpers such as ZstdBinding) shadow nothing, so there
    // is no API to compare them against.
    val (overrides, additions) = versioned.partition(baseNames.contains)

    val drift = overrides.flatMap { name =>
      val fqcn = fqcnOf(name)
      val b    = api(baseRelease, fqcn)
      val v    = api(ffmRelease, fqcn)
      val diff = (b diff v).map("  base only: " + _) ++ (v diff b).map(s"  java$ffmRelease only: " + _)
      if (diff.isEmpty) None
      else Some(s"$fqcn public API differs between the base and the JDK $ffmRelease version:\n" +
        diff.mkString("\n"))
    }

    // ... but they must not be public either, or JDK 22+ would see a type no other
    // runtime has - an API difference the comparison above cannot catch, since
    // there is no base class to compare with.
    val leaked = additions.map(fqcnOf)
      .filter(fqcn => javapLines(ffmRelease, fqcn).headOption.exists(_.startsWith("public ")))
      .map(fqcn => s"$fqcn exists only in the JDK $ffmRelease tree and is public; " +
        "versioned-only classes must be package-private.")

    val problems = drift ++ leaked
    if (problems.nonEmpty) sys.error(s"${jar.getName}:\n" + problems.mkString("\n\n"))

    log.info(s"Multi-Release check: ${jar.getName} - ${overrides.size} class(es) resolve to $prefix on " +
      s"$ffmRelease and match the base public API" +
      (if (additions.nonEmpty) s", ${additions.size} package-private helper class(es)" else "") + ".")
  } finally jf.close()
}
