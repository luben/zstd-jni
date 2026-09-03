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
        // The FFM implementation looks the ZSTD_* symbols up in this library, so
        // they must be in the DLL's export table. The version script below cannot
        // put them there: on PE, ld auto-exports every global symbol only when the
        // DLL would otherwise export nothing, and JNIEXPORT is __declspec(dllexport)
        // here, so the Java_* functions already switch that off - leaving
        // --version-script able only to filter. zstd's own DLL switch adds them, by
        // making ZSTDLIB_API and ZSTDLIB_STATIC_API expand to __declspec(dllexport).
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

// Multi-Release JAR: the JDK 22+ implementation on top of the FFM API.
//
// 22 is where the Foreign Function & Memory API was finalized (JEP 454). These
// sources are compiled with `--release 22` (the rest of the project with
// `--release 8`) into META-INF/versions/22, where a JDK 22+ runtime picks them
// over their same-named counterparts in the jar root.

val ffmRelease = "22"

lazy val ffmSourceDir = settingKey[File]("Source directory of the JDK 22+ (FFM) implementation")
lazy val ffmClassDir  = settingKey[File]("Where the versioned classes are written, inside the jar content root")
lazy val ffmCompile   = taskKey[Seq[File]](s"Compile the FFM sources into META-INF/versions/$ffmRelease")

ffmSourceDir := (Compile / sourceDirectory).value / s"java$ffmRelease"

// Deliberately inside Compile/classDirectory: that directory *is* the jar content
// root, so the versioned classes flow into every jar with no extra mappings - the
// same trick `jniBinPath` uses for the .so and ModuleInfoPlugin for
// module-info.class. It also keeps them inert on the compile/test classpath,
// since a directory entry only ever resolves com/github/luben/zstd/Foo.class.
// Tooling that walks the directory instead is the exception - see the
// jacoco{Excludes,InstrumentationExcludes} note below.
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

// Everything this build asserts about the versioned classes, asserted against the
// finished jar - the only artifact that can actually be wrong.
//
// Writing classes into META-INF/versions/22 does not by itself make a jar dispatch
// to them: a lost `Multi-Release: true`, entries at the wrong path or a jar packaged
// on a JDK below 22 all produce an artifact that quietly loads the JNI
// implementation everywhere - and passes every test, because the tests do not care
// which implementation answers. Nor does anything at runtime enforce JEP 238's rule
// that a versioned class expose the same public API as the class it overrides; a
// test run only proves that the members the suite happens to call are present.
//
// Three questions, one jar:
//
//   1. META-INF/versions/22 is not empty       entries()
//   2. every class in it wins on 22            getJarEntry().getRealName()
//   3. its public API matches the base copy    javap --multi-release 8 vs 22
//
// (2) is the JDK's own Multi-Release resolution, manifest attribute included - the
// same question a consumer's class loader asks, answered in-process. (3) reads both
// copies out of that same jar rather than out of target/classes, so it compares what
// actually ships. Only release 22 is asked: "the base copy wins below 22" could only
// be checked against a list of base copies read from the same jar, so it would
// assert the JDK's own resolution rather than anything about the artifact.
//
// It runs from `packageBin`, so it cannot be skipped: every jar this build writes is
// checked, published or not - `publishSigned` and a local `sbt package` included.
def verifyMultiRelease(jar: File, log: Logger): Unit = {
  val prefix = s"META-INF/versions/$ffmRelease/"
  // What a runtime below 22 resolves to, and so what `javap --multi-release` has to
  // be asked for to see the base copy.
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

  // `javap -public` prints the type declaration, then one line per public member.
  // Both matter: the members are the API, the declaration says whether the type
  // itself is public. `--multi-release` chooses which copy of the class to read, so
  // the base and the versioned one come out of the same file.
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

  // `native` is normalised away: dropping it is not a binary-compatibility change,
  // it is precisely what this port does. Sorting compares the set of members
  // rather than their declaration order.
  def api(release: String, fqcn: String): Seq[String] =
    javapLines(release, fqcn).map(_.replace("native ", "")).sorted

  def fqcnOf(entry: String): String = entry.stripSuffix(".class").replace('/', '.')

  val jf = new java.util.jar.JarFile(
    jar, true, java.util.zip.ZipFile.OPEN_READ, java.lang.Runtime.Version.parse(ffmRelease))
  try {
    // One pass over the jar as it is on disk: the versioned classes, and the base
    // classes they might be overriding. Note this has to read `entries()` - on this
    // handle `getEntry` would apply release-22 resolution and report every versioned
    // class as having a base copy. module-info is excluded, the JDK versions it by
    // its own rules.
    val (versioned, baseNames) = {
      val v  = List.newBuilder[String]
      val b  = Set.newBuilder[String]
      val en = jf.entries()
      while (en.hasMoreElements) {
        val n = en.nextElement().getName
        if (n.endsWith(".class") && !n.endsWith("module-info.class")) {
          if (n.startsWith(prefix)) v += n.substring(prefix.length)
          else if (!n.startsWith("META-INF/versions/")) b += n
        }
      }
      (v.result().sorted, b.result())
    }

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
      if (b == v) None
      else Some(
        s"$fqcn public API differs between the base and the JDK $ffmRelease version:\n" +
        (b diff v).map("  base only: " + _).mkString("\n") +
        (if ((b diff v).nonEmpty && (v diff b).nonEmpty) "\n" else "") +
        (v diff b).map(s"  java$ffmRelease only: " + _).mkString("\n")
      )
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

// `sbt test` runs against target/classes, which is the JNI implementation and
// only ever that: a directory entry resolves com/github/luben/zstd/Foo.class and
// never the META-INF/versions/22 copy next to it. Multi-Release dispatch is a jar
// feature, so the FFM implementation is tested by running the same suite against
// the packaged jar - see the `testFromJar` input task below.
//
// The FFM sources are still compiled on every `test`, so a mistake in them fails the
// ordinary local build rather than waiting for a package. (`ffmCompile` depends on
// `Compile / compile`, so this hangs off `Test / test`, not off `compile`, to avoid
// a cycle.) Whether they are a *valid* override is `verifyMultiRelease`'s question,
// asked of the jar - `test` produces no jar, so it can ship nothing.
Test / test := (Test / test).dependsOn(ffmCompile).value

// The FFM sources are deliberately not in unmanagedSourceDirectories - the base
// compile runs with `--release 8` and would reject java.lang.foreign - so they
// go into the sources jar by hand, under META-INF/versions to mirror the class
// layout; at the root they would collide with the base sources.
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

// Jacoco walks Compile/classDirectory as a *directory tree*, not as a classpath
// entry, so it also picks up the META-INF/versions/<n> copies; two files with the
// same class name in one CoverageBuilder fails with "Can't add different class
// with same name". Its filters match the path relative to classDirectory with the
// separators turned into dots, so the versioned copy is
// "META-INF.versions.22.com.github.luben.zstd.Foo" and only it is dropped.
//
// Right on the merits too: `sbt jacoco` exercises the JNI path, so the versioned
// classes would otherwise land in the report as 0%-covered duplicates of classes
// that *are* covered. Measuring the FFM path needs its own exec file and bundle.
// Not pinned to ffmRelease - it must cover any future META-INF/versions/<n>.
val versionedClasses = "META-INF.versions.*"
jacocoExcludes := Seq(versionedClasses)
jacocoInstrumentationExcludes := Seq("module-info", versionedClasses)

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

// Two tasks write into target/classes without going through `compile`: jniCompile
// drops the native library at <os>/<arch>/, and ffmCompile writes
// META-INF/versions/22. `mappings` is what reads that directory, and as a
// *sibling* dependency of packageBin it is free to run before either of them,
// producing a jar quietly missing the .so, the versioned classes or both - sbt
// drops a mapping whose source file does not exist without a word. The edge
// therefore goes on `mappings`, not on `packageBin`. The classified configs need
// it just as much: their `classes` list is a lazy val snapshotting target/classes
// the first time any config forces it.
(Compile +: classifiedConfigs).flatMap(c => Seq(
  c / packageBin / mappings := (c / packageBin / mappings).dependsOn(jniCompile, ffmCompile).value,
  c / packageBin := {
    val jar = (c / packageBin).value
    verifyMultiRelease(jar, streams.value.log)
    jar
  }
)) ++
  // Each classified config gets its own packageOptions from
  // Defaults.compileSettings, so it never delegates to the Compile-scoped
  // manifest above and Multi-Release has to be set on each one explicitly.
  classifiedConfigs.map(c => c / packageBin / packageOptions += multiReleaseAttribute)

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

// Runs the suite against the packaged jar rather than against target/classes. This
// is the only thing that exercises Multi-Release dispatch the way a consumer does:
// the JDK picks the implementation out of the jar, so which one answers is decided
// by the feature version of the JVM passed in and by nothing else.
//
//   testFromJar <jdk22+>                                          -> FFM
//   testFromJar <jdk22+> -Djdk.util.jar.enableMultiRelease=false  -> JNI
//   testFromJar <jdk8-21>                                         -> JNI
//
// The three differ only in the runtime, never in the artifact. It launches a JVM
// of its own rather than reusing `Test / test`, which sbt runs in-process and
// would pin to whatever JDK built the jar.
//
// It takes nothing from the compile graph - the jar path and the test class
// directory are settings, and the dependency jars come from `update`. That is
// deliberate: sbt caches task results per command rather than per session, so a
// dependency on `packageBin` or on `Test / fullClasspath` would re-run jniCompile,
// a full gcc+LTO build of libzstd, once per run. `testFromJarSetup` does that work
// once; this reads what it left behind, and re-applies verifyMultiRelease to the
// artifact it is about to test - the stronger guarantee anyway, since a green FFM
// run then cannot mean "resolved to the base class because there was nothing else".
//
// The java home defaults to JAVA_HOME, then to the JDK running sbt, so a bare
// `testFromJar` does the useful thing locally.
lazy val testFromJarSetup = taskKey[Unit]("Package the jar and compile the tests, ready for testFromJar")
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
  if (!jar.isFile || !(testClasses ** "*.class").get.nonEmpty)
    sys.error(s"$jar or $testClasses is missing - run `sbt testFromJarSetup` first")
  verifyMultiRelease(jar, log)

  // The resolved dependency jars plus the test classes, and no other product
  // directory: the whole point is that com.github.luben.zstd comes out of the jar
  // now. Taking the external classpath rather than filtering the full one also
  // sidesteps sbt-jacoco, whose Test/fullClasspath wrapper swaps target/classes for
  // target/jacoco/instrumented-classes and would otherwise put the JNI
  // implementation back in front of the jar - green, and meaningless.
  //
  // The test classes have to be on -cp and not only on scalatest's -R runpath. The
  // suite is itself in package com.github.luben.zstd and calls package-private
  // members; loaded from the runpath it lands in a different runtime package from
  // the jar's copy, and those calls throw IllegalAccessError.
  val entries = (testClasses +: (Test / externalDependencyClasspath).value.files).map(_.getCanonicalFile)

  // Backstop, asked semantically rather than by path shape.
  val leaked = entries.filter(e => (e / "com" / "github" / "luben" / "zstd" / "Zstd.class").isFile)
  if (leaked.nonEmpty)
    sys.error("these classpath entries would shadow the jar's own classes:\n" +
              leaked.map("  " + _).mkString("\n"))

  // A leading non-flag argument is the java home; without one, fall back to
  // JAVA_HOME and then to the JDK running sbt.
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
    // JEP 472: restricted methods warn on 24 and are to be blocked later. Both
    // implementations need this - System.load for JNI, the downcalls for FFM -
    // and JDKs below 22 reject the flag outright.
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

// So CI can multi-release-check every published jar, not just the main one. Not
// `packagedArtifacts`, which would also run aarTask and need the Android SDK.
lazy val packageClassified = taskKey[Seq[File]]("Package every classified jar")
packageClassified := packageBin.all(
  ScopeFilter(configurations = inConfigurations(classifiedConfigs: _*))
).value
