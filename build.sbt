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

// ---------------------------------------------------------------------------
// Multi-Release JAR: the JDK 22+ implementation on top of the FFM API
// ---------------------------------------------------------------------------
//
// JDK 22 is the baseline because that is where the Foreign Function & Memory
// API was finalized (JEP 454).
//
// These sources are compiled with `--release 22` (the rest of the project is
// built with `--release 8`) and land in META-INF/versions/22, where a JDK 22+
// runtime picks them over their same-named counterparts in the jar root.

val ffmRelease = "22"

lazy val ffmSourceDir = settingKey[File]("Source directory of the JDK 22+ (FFM) implementation")
lazy val ffmClassDir  = settingKey[File]("Where the versioned classes are written, inside the jar content root")
lazy val ffmCompile   = taskKey[Seq[File]](s"Compile the FFM sources into META-INF/versions/$ffmRelease")
lazy val ffmApiCheck  = taskKey[Unit]("Fail if a versioned class alters the public API of the class it overrides")

ffmSourceDir := (Compile / sourceDirectory).value / s"java$ffmRelease"

// Deliberately inside Compile/classDirectory: that directory *is* the jar
// content root, so the versioned classes flow into `packageBin` and into every
// classified jar with no extra mappings. The build already relies on this twice
// - `jniBinPath` drops the .so at target/classes/<os>/<arch>/, and
// ModuleInfoPlugin writes target/classes/module-info.class.
//
// It also keeps them inert on the ordinary compile/test classpath: as a
// directory entry, target/classes only ever resolves
// com/github/luben/zstd/Foo.class, never the META-INF/versions/22 copy.
ffmClassDir := (Compile / classDirectory).value / "META-INF" / "versions" / ffmRelease

ffmCompile := {
  val log     = streams.value.log
  val out     = ffmClassDir.value
  val sources = (ffmSourceDir.value ** "*.java").get
  // The FFM sources reference BufferPool, Zstd, Native, ... so the base classes
  // have to exist first.
  val baseClasses = (Compile / classDirectory).value
  val _           = (Compile / compile).value
  // Hoisted: sbt evaluates task dependencies regardless of the branch taken.
  val depCp       = (Compile / dependencyClasspath).value.files

  if (sources.isEmpty) {
    IO.delete(out)
    Seq.empty[File]
  } else if (!scala.util.Properties.isJavaAtLeast(ffmRelease)) {
    // CI still builds every job on JDK 11; skipping keeps it green rather than
    // failing the whole build on a source set it cannot compile.
    log.warn(s"JDK $ffmRelease+ is required to build the FFM sources, but this is " +
             s"JDK ${sys.props.getOrElse("java.version", "?")}.")
    log.warn(s"Skipping - the jar will NOT contain META-INF/versions/$ffmRelease and " +
             s"JDK $ffmRelease+ runtimes will fall back to the JNI implementation.")
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

// A versioned class must expose exactly the same public API as the class it
// overrides; nothing enforces that at runtime, so enforce it at build time.
ffmApiCheck := {
  val log         = streams.value.log
  val versioned   = ffmCompile.value
  val out         = ffmClassDir.value
  val baseClasses = (Compile / classDirectory).value

  val javap = java.util.spi.ToolProvider.findFirst("javap")
    .orElseThrow(() => new RuntimeException("javap tool not available"))

  // `javap -public` prints the type declaration first, then one line per public
  // member. Both matter here: the members are the API, the declaration says
  // whether the type itself is public.
  def javapLines(dir: File, fqcn: String): Seq[String] = {
    val buf = new java.io.StringWriter
    val pw  = new java.io.PrintWriter(buf)
    val rc  = javap.run(pw, pw, "-public", "-cp", dir.getAbsolutePath, fqcn)
    pw.flush()
    if (rc != 0) sys.error(s"javap failed on $fqcn in $dir (exit code $rc):\n${buf.toString}")
    buf.toString.linesIterator
      .map(_.trim)
      .filter(_.nonEmpty)
      .filterNot(_.startsWith("Compiled from"))
      .toSeq
  }

  // `native` is normalised away: it is a method flag javap prints, but dropping
  // it is not a binary-compatibility change - it is precisely what this port
  // does. Sorting compares the set of members, not their declaration order.
  def api(dir: File, fqcn: String): Seq[String] =
    javapLines(dir, fqcn).map(_.replace("native ", "")).sorted

  def fqcnOf(cls: File): String =
    cls.relativeTo(out).get.getPath.stripSuffix(".class").replace(java.io.File.separatorChar, '.')

  // Classes that exist only in the versioned tree (helpers such as ZstdBinding)
  // shadow nothing, so there is no API to compare them against.
  val (overrides, additions) =
    versioned.partition(cls => (baseClasses / cls.relativeTo(out).get.getPath).isFile)

  val drift = overrides.flatMap { cls =>
    val fqcn = fqcnOf(cls)
    val b    = api(baseClasses, fqcn)
    val v    = api(out, fqcn)
    if (b == v) None
    else Some(
      s"$fqcn public API differs between the base and the JDK $ffmRelease version:\n" +
      (b diff v).map("  base only: " + _).mkString("\n") +
      (if ((b diff v).nonEmpty && (v diff b).nonEmpty) "\n" else "") +
      (v diff b).map(s"  java$ffmRelease only: " + _).mkString("\n")
    )
  }

  // ... but they must not be public either, or JDK 22+ would see a type that no
  // other runtime has - which is an API difference the comparison above cannot
  // catch, because there is no base class to compare with.
  val leaked = additions.map(fqcnOf).filter(fqcn => javapLines(out, fqcn).headOption.exists(_.startsWith("public ")))
    .map(fqcn => s"$fqcn exists only in the JDK $ffmRelease tree and is public; " +
                 "versioned-only classes must be package-private.")

  val problems = drift ++ leaked
  if (problems.nonEmpty) sys.error(problems.mkString("\n\n"))
  else if (versioned.nonEmpty)
    log.info(s"FFM API check: ${overrides.size} overriding classes match the base API" +
             (if (additions.nonEmpty) s", ${additions.size} versioned-only helper classes" else "") + ".")
}

Compile / packageBin := (Compile / packageBin).dependsOn(ffmApiCheck).value

// Run the existing test suite against the FFM classes by prepending the
// versioned directory to the test classpath, so its classes shadow the base
// ones by fully-qualified name:  ./sbt -Dzstd.ffm=true test
lazy val ffmTest = settingKey[Boolean]("Run the test suite against the FFM classes instead of the JNI ones")

// Present and not explicitly "false", so `-Dzstd.ffm`, `-Dzstd.ffm=true` and
// `-Dzstd.ffm=1` all select the FFM path and only `-Dzstd.ffm=false` opts out.
ffmTest := sys.props.get("zstd.ffm").exists(!_.equalsIgnoreCase("false"))

// Mirrors sbt's own definition (concatDistinct of exportedProducts and
// dependencyClasspath) so that the versioned directory can be put in front.
Test / fullClasspath := {
  val base    = ((Test / exportedProducts).value ++ (Test / dependencyClasspath).value).distinct
  // Hoisted out of the branch: sbt evaluates task dependencies either way, so the
  // FFM sources are compiled and API-checked on every `test` run, not only when
  // they are the ones under test.
  val ffm     = ffmCompile.value
  val _       = ffmApiCheck.value
  val ffmDir  = ffmClassDir.value
  val log     = streams.value.log
  if (!ffmTest.value) base
  else if (ffm.isEmpty) sys.error(s"-Dzstd.ffm=true but no FFM classes were built (needs JDK $ffmRelease+)")
  else {
    log.info(s"Testing against the FFM implementation in $ffmDir")
    Attributed.blank(ffmDir) +: base
  }
}

// The FFM sources are deliberately not in unmanagedSourceDirectories - the base
// compile runs with `--release 8` and would reject java.lang.foreign - so they
// have to be added to the sources jar by hand. They go under META-INF/versions,
// mirroring the class layout: at the root they would collide with the base
// sources of the same name.
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
jacocoInstrumentationExcludes := Seq("module-info")

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


// Every one of these configs gets its own `packageBin / packageOptions` from
// Defaults.compileSettings, so it never delegates to the Compile-scoped manifest
// above and Multi-Release has to be set on each of them explicitly.
//
// The dependency on ffmApiCheck is what makes their *contents* right: their
// `mappings` is the plain `classes` file list with no link to `compile`, and
// `classes` is a lazy val that snapshots target/classes the first time any of
// them forces it. Without the edge, that snapshot can be taken before the
// versioned classes have been written, and the jar ships the Multi-Release
// manifest attribute with no META-INF/versions/22 behind it.
Seq(
  Linux_amd64, Linux_i386, Linux_aarch64, Linux_arm, Linux_ppc64le, Linux_ppc64,
  Linux_mips64, Linux_loongarch64, Linux_s390x, Linux_riscv64, Aix_ppc64,
  Darwin_x86_64, Darwin_aarch64, FreeBSD_amd64, FreeBSD_i386,
  Win_x86, Win_amd64, Win_aarch64, Cloud
).flatMap(c => Seq(
  c / packageBin / packageOptions += multiReleaseAttribute,
  c / packageBin := (c / packageBin).dependsOn(ffmApiCheck).value
))
