val nameValue = "zstd-jni"

name := nameValue

version := {
  scala.io.Source.fromFile("version").getLines.next
}

scalaVersion := "2.12.13"

enablePlugins(JniPlugin, SbtOsgi)

autoScalaLibrary := false

crossPaths := false

logBuffered in Test := false

parallelExecution in Test := false

libraryDependencies ++= Seq(
  "org.scalatest"  %% "scalatest"  % "3.0.5"  % "test",
  "org.scalacheck" %% "scalacheck" % "1.14.1" % "test"
)

javacOptions ++= Seq("-source", "1.6", "-target", "1.6", "-Xlint:unchecked")

javacOptions in doc := Seq("-source", "1.6")

// fork := true
// Check at runtime for JNI errors when running tests
javaOptions in Test ++= Seq("-Xcheck:jni")

// sbt-jni configuration
jniLibraryName := "zstd-jni"

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

jniCppExtensions := Seq("c")

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
      Seq("-D_JNI_IMPLEMENTATION_", "-Wl,--kill-at", "-static-libgcc")
  else if (System.getProperty("os.name").toLowerCase startsWith "mac")
    // MacOS uses clang that does not support the "-static-libgcc" option
    jniGccFlags.value
  else
    jniGccFlags.value ++ Seq("-static-libgcc")
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

// Do no generate C header files - we don't have use of them.
// There is also a compatibility problem - newer JDKs don't have `javah`
jniGenerateHeaders := false

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
  """aix/ppc64/libzstd-jni.so;osname=AIX;processor=ppc64,
    |darwin/x86_64/libzstd-jni.dylib;osname=MacOS;osname=MacOSX;processor=x86_64,
    |darwin/aarch64/libzstd-jni.dylib;osname=MacOS;osname=MacOSX;processor=aarch64,
    |freebsd/amd64/libzstd-jni.so;osname=FreeBSD;processor=amd64,
    |freebsd/i386/libzstd-jni.so;osname=FreeBSD;processor=i386,
    |linux/aarch64/libzstd-jni.so;osname=Linux;processor=aarch64,
    |linux/amd64/libzstd-jni.so;osname=Linux;processor=amd64,
    |linux/arm/libzstd-jni.so;osname=Linux;processor=arm,
    |linux/i386/libzstd-jni.so;osname=Linux;processor=i386,
    |linux/mips64/libzstd-jni.so;osname=Linux;processor=mips64,
    |linux/ppc64/libzstd-jni.so;osname=Linux;processor=ppc64,
    |linux/ppc64le/libzstd-jni.so;osname=Linux;processor=ppc64le,
    |linux/s390x/libzstd-jni.so;osname=Linux;processor=s390x,
    |win/amd64/libzstd-jni.dll;osname=Win32;processor=amd64,
    |win/x86/libzstd-jni.dll;osname=Win32;processor=x86""".stripMargin),
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
OsgiKeys.privatePackage := Seq("include",
    "linux.amd64", "linux.i386", "linux.aarch64", "linux.arm", "linux.ppc64",
    "linux.ppc64le", "linux.mips64", "linux.s390x", "aix.ppc64", "darwin.x86_64",
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
  (file("target/classes/linux/amd64/libzstd-jni.so"), "linux/amd64/libzstd-jni.so") :: classes
}
packageOptions in (Linux_amd64, packageBin) ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "linux_amd64"), packageBin in Linux_amd64)

lazy val Linux_i386 = config("linux_i386").extend(Compile)
inConfig(Linux_i386)(Defaults.compileSettings)
mappings in (Linux_i386, packageBin) := {
  (file("target/classes/linux/i386/libzstd-jni.so"), "linux/i386/libzstd-jni.so") :: classes
}
packageOptions in (Linux_i386, packageBin) ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "linux_i386"), packageBin in Linux_i386)

lazy val Linux_aarch64 = config("linux_aarch64").extend(Compile)
inConfig(Linux_aarch64)(Defaults.compileSettings)
mappings in (Linux_aarch64, packageBin) := {
  (file("target/classes/linux/aarch64/libzstd-jni.so"), "linux/aarch64/libzstd-jni.so") :: classes
}
packageOptions in (Linux_aarch64, packageBin) ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "linux_aarch64"), packageBin in Linux_aarch64)

lazy val Linux_arm = config("linux_arm").extend(Compile)
inConfig(Linux_arm)(Defaults.compileSettings)
mappings in (Linux_arm, packageBin) := {
  (file("target/classes/linux/arm/libzstd-jni.so"), "linux/arm/libzstd-jni.so") :: classes
}
packageOptions in (Linux_arm, packageBin) ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "linux_arm"), packageBin in Linux_arm)

lazy val Linux_ppc64le = config("linux_ppc64le").extend(Compile)
inConfig(Linux_ppc64le)(Defaults.compileSettings)
mappings in (Linux_ppc64le, packageBin) := {
  (file("target/classes/linux/ppc64le/libzstd-jni.so"), "linux/ppc64le/libzstd-jni.so") :: classes
}
packageOptions in (Linux_ppc64le, packageBin) ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "linux_ppc64le"), packageBin in Linux_ppc64le)

lazy val Linux_ppc64 = config("linux_ppc64").extend(Compile)
inConfig(Linux_ppc64)(Defaults.compileSettings)
mappings in (Linux_ppc64, packageBin) := {
  (file("target/classes/linux/ppc64/libzstd-jni.so"), "linux/ppc64/libzstd-jni.so") :: classes
}
packageOptions in (Linux_ppc64, packageBin) ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "linux_ppc64"), packageBin in Linux_ppc64)

lazy val Linux_mips64 = config("linux_mips64").extend(Compile)
inConfig(Linux_mips64)(Defaults.compileSettings)
mappings in (Linux_mips64, packageBin) := {
  (file("target/classes/linux/mips64/libzstd-jni.so"), "linux/mips64/libzstd-jni.so") :: classes
}
packageOptions in (Linux_mips64, packageBin) ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "linux_mips64"), packageBin in Linux_mips64)

lazy val Linux_s390x = config("linux_s390x").extend(Compile)
inConfig(Linux_s390x)(Defaults.compileSettings)
mappings in (Linux_s390x, packageBin) := {
  (file("target/classes/linux/s390x/libzstd-jni.so"), "linux/s390x/libzstd-jni.so") :: classes
}
packageOptions in (Linux_s390x, packageBin) ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "linux_s390x"), packageBin in Linux_s390x)

lazy val Aix_ppc64 = config("aix_ppc64").extend(Compile)
inConfig(Aix_ppc64)(Defaults.compileSettings)
mappings in (Aix_ppc64, packageBin) := {
  (file("target/classes/aix/ppc64/libzstd-jni.so"), "aix/ppc64/libzstd-jni.so") :: classes
}
packageOptions in (Aix_ppc64, packageBin) ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "aix_ppc64"), packageBin in Aix_ppc64)

lazy val Darwin_x86_64 = config("darwin_x86_64").extend(Compile)
inConfig(Darwin_x86_64)(Defaults.compileSettings)
mappings in (Darwin_x86_64, packageBin) := {
  (file("target/classes/darwin/x86_64/libzstd-jni.dylib"), "darwin/x86_64/libzstd-jni.dylib") :: classes
}
packageOptions in (Darwin_x86_64, packageBin) ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "darwin_x86_64"), packageBin in Darwin_x86_64)

lazy val Darwin_aarch64 = config("darwin_aarch64").extend(Compile)
inConfig(Darwin_aarch64)(Defaults.compileSettings)
mappings in (Darwin_aarch64, packageBin) := {
  (file("target/classes/darwin/aarch64/libzstd-jni.dylib"), "darwin/aarch64/libzstd-jni.dylib") :: classes
}
packageOptions in (Darwin_aarch64, packageBin) ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "darwin_aarch64"), packageBin in Darwin_aarch64)

lazy val FreeBSD_amd64 = config("freebsd_amd64").extend(Compile)
inConfig(FreeBSD_amd64)(Defaults.compileSettings)
mappings in (FreeBSD_amd64, packageBin) := {
  (file("target/classes/freebsd/amd64/libzstd-jni.so"), "freebsd/amd64/libzstd-jni.so") :: classes
}
packageOptions in (FreeBSD_amd64, packageBin) ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "freebsd_amd64"), packageBin in FreeBSD_amd64)

lazy val FreeBSD_i386 = config("freebsd_i386").extend(Compile)
inConfig(FreeBSD_i386)(Defaults.compileSettings)
mappings in (FreeBSD_i386, packageBin) := {
  (file("target/classes/freebsd/i386/libzstd-jni.so"), "freebsd/i386/libzstd-jni.so") :: classes
}
packageOptions in (FreeBSD_i386, packageBin) ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "freebsd_i386"), packageBin in FreeBSD_i386)

val Win_x86 = config("win_x86").extend(Compile)
inConfig(Win_x86)(Defaults.compileSettings)
mappings in (Win_x86, packageBin) := {
  (file("target/classes/win/x86/libzstd-jni.dll"), "win/x86/libzstd-jni.dll") :: classes
}
packageOptions in (Win_x86, packageBin) ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "win_x86"), packageBin in Win_x86)

val Win_amd64 = config("win_amd64").extend(Compile)
inConfig(Win_amd64)(Defaults.compileSettings)
mappings in (Win_amd64, packageBin) := {
  (file("target/classes/win/amd64/libzstd-jni.dll"), "win/amd64/libzstd-jni.dll") :: classes
}
packageOptions in (Win_amd64, packageBin) ++= Seq(
  Package.ManifestAttributes(new java.util.jar.Attributes.Name("Automatic-Module-Name") -> "com.github.luben.zstd_jni"),
)
addArtifact(Artifact(nameValue, "win_amd64"), packageBin in Win_amd64)
