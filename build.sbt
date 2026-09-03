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
        // they have to be in the DLL's export table. On PE the version script
        // below cannot put them there: ld only auto-exports every global symbol
        // when the DLL would otherwise export nothing, and JNIEXPORT is
        // __declspec(dllexport) here, so the Java_* functions already switch
        // that off. --version-script can only filter the auto-exported set.
        // zstd's own DLL switch is the way to add them: it makes ZSTDLIB_API and
        // ZSTDLIB_STATIC_API expand to __declspec(dllexport), which exports
        // libzstd's public and static API and nothing else.
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
//
// The exception is tooling that walks the directory instead of resolving through
// it - see the jacoco{Excludes,InstrumentationExcludes} note further down.
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

// Writing classes into META-INF/versions/22 does not by itself make a jar
// dispatch to them. A lost `Multi-Release: true`, entries at the wrong path, or
// a jar packaged on a JDK below 22 all produce an artifact that quietly loads
// the JNI implementation on every runtime - and passes every test, because the
// tests do not care which implementation answers.
//
// java.util.jar.JarFile(file, verify, mode, Runtime.Version) applies the JDK's
// own resolution, manifest attribute included, and getRealName() reports the
// physical entry it chose. That is the same question a consumer's class loader
// asks, answered in-process against the finished artifact.
//
// This runs from `packageBin` rather than from a CI script so that it cannot be
// skipped: every jar this build produces, published or not, is checked as it is
// written.
def verifyMultiRelease(jar: File, log: Logger): Unit = {
  import java.util.jar.JarFile
  import java.util.zip.ZipFile

  val prefix = s"META-INF/versions/$ffmRelease/"

  // Below 22 `ffmCompile` skips and says so, so an empty jar is expected rather
  // than broken. Everything that publishes builds on a current JDK.
  if (!scala.util.Properties.isJavaAtLeast(ffmRelease)) {
    log.warn(s"${jar.getName}: built on JDK ${sys.props.getOrElse("java.version", "?")}, " +
             s"so it carries no META-INF/versions/$ffmRelease and every runtime will get the JNI classes.")
    return
  }

  // One pass over the raw entry names: which classes live under
  // META-INF/versions/22, and which of those also have a base copy at the root.
  // module-info is excluded - the JDK versions it by its own rules.
  val (versioned, rootNames) = {
    val zip = new ZipFile(jar)
    try {
      val v = scala.collection.mutable.ListBuffer.empty[String]
      val r = scala.collection.mutable.HashSet.empty[String]
      val en = zip.entries()
      while (en.hasMoreElements) {
        val n = en.nextElement().getName
        if (n.endsWith(".class") && !n.endsWith("module-info.class")) {
          if (n.startsWith(prefix)) v += n.substring(prefix.length) else r += n
        }
      }
      (v.toList.sorted, r.toSet)
    } finally zip.close()
  }

  if (versioned.isEmpty)
    sys.error(s"${jar.getName} has no $prefix*.class entries, so JDK $ffmRelease+ would load the " +
              "JNI implementation from the jar root. Was it packaged before ffmCompile ran?")

  val atFfm  = new JarFile(jar, true, ZipFile.OPEN_READ, java.lang.Runtime.Version.parse(ffmRelease))
  val atBase = new JarFile(jar, true, ZipFile.OPEN_READ, java.lang.Runtime.Version.parse("8"))
  try {
    def resolves(jf: JarFile, name: String): Option[String] =
      Option(jf.getJarEntry(name)).map(_.getRealName)

    val problems = versioned.flatMap { name =>
      // On 22 the versioned copy has to win, for every class ...
      val onFfm = resolves(atFfm, name)
      val ffmProblem =
        if (onFfm.contains(prefix + name)) None
        else Some(s"  $name on $ffmRelease resolves to ${onFfm.getOrElse("<absent>")}, expected $prefix$name")

      // ... and on 8 the base copy has to, or nothing at all: a versioned-only
      // helper such as ZstdBinding must be invisible to a pre-22 runtime.
      val onBase   = resolves(atBase, name)
      val expected = if (rootNames.contains(name)) Some(name) else None
      val baseProblem =
        if (onBase == expected) None
        else Some(s"  $name on 8 resolves to ${onBase.getOrElse("<absent>")}, " +
                  s"expected ${expected.getOrElse("<absent>")}")

      ffmProblem ++ baseProblem
    }

    if (problems.nonEmpty)
      sys.error(s"${jar.getName} does not dispatch as a Multi-Release JAR - is `Multi-Release: true` " +
                s"in its manifest?\n" + problems.mkString("\n"))

    val overrides = versioned.count(rootNames.contains)
    log.info(s"Multi-Release check: ${jar.getName} - ${versioned.size} class(es) resolve to $prefix on " +
             s"$ffmRelease, $overrides of them back to the jar root on 8.")
  } finally {
    atFfm.close()
    atBase.close()
  }
}

// Two tasks write into target/classes without going through `compile`:
// `jniCompile` drops the native library at <os>/<arch>/, and ffmCompile (behind
// ffmApiCheck) writes META-INF/versions/22. `mappings` reads that directory, and
// as a *sibling* dependency of packageBin it is free to run before either of
// them - which produces a jar that is quietly missing the .so, the versioned
// classes, or both, and no packaging step complains. The edge therefore belongs
// on `mappings` rather than on `packageBin`.
Compile / packageBin / mappings :=
  (Compile / packageBin / mappings).dependsOn(jniCompile, ffmApiCheck).value

Compile / packageBin := {
  val jar = (Compile / packageBin).value
  verifyMultiRelease(jar, streams.value.log)
  jar
}

// `sbt test` runs against target/classes, which is the JNI implementation and
// only ever that: a directory entry on the classpath resolves
// com/github/luben/zstd/Foo.class and never the META-INF/versions/22 copy next
// to it. Multi-Release dispatch is a jar feature, so the FFM implementation is
// tested by running the same suite against the packaged jar under a JDK 22+
// runtime - see `testFromJarSetup` below and .github/scripts/RunTestsFromJar.java.
//
// The FFM sources are still compiled and API-checked on every `test`, so a
// mistake in them fails the ordinary local build rather than waiting for the
// packaging step. (`ffmCompile` depends on `Compile / compile`, so this has to
// hang off `Test / test` rather than off `compile`, or it would cycle.)
Test / test := (Test / test).dependsOn(ffmApiCheck).value

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

// Jacoco walks Compile/classDirectory as a *directory tree*, not as a classpath
// entry, so it also picks up the META-INF/versions/<n> copies. Both files carry
// the same class name, and feeding two of those to one CoverageBuilder fails with
// "Can't add different class with same name".
//
// Its filters match on the path relative to classDirectory with the separators
// turned into dots, so the versioned copy is
// "META-INF.versions.22.com.github.luben.zstd.Foo" while the base one stays
// "com.github.luben.zstd.Foo" - which is what lets us drop only the former.
//
// Dropping it is also what we want on the merits: `sbt jacoco` exercises the JNI
// path, where the versioned classes are never loaded, so they would otherwise
// land in the report as 0%-covered duplicates of classes that *are* covered.
// Measuring the FFM path needs its own exec file and its own bundle.
// Deliberately not pinned to ffmRelease: it must keep covering any future
// META-INF/versions/<n> directory the build grows.
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


// Every one of these configs gets its own `packageBin / packageOptions` from
// Defaults.compileSettings, so it never delegates to the Compile-scoped manifest
// above and Multi-Release has to be set on each of them explicitly.
//
// The dependency on jniCompile and ffmApiCheck is what makes their *contents*
// right, and it is on `mappings` for the reason given above the Compile-scoped
// one: their `mappings` names the native library by literal path and takes the
// class list from `classes`, a lazy val that snapshots target/classes the first
// time any config forces it - and sbt silently drops a mapping whose source file
// does not exist. Without the edge the snapshot can be taken before either
// writer has run, and the jar ships the Multi-Release manifest attribute with no
// META-INF/versions/22 behind it, or no .so, or neither.
val classifiedConfigs = Seq(
  Linux_amd64, Linux_i386, Linux_aarch64, Linux_arm, Linux_ppc64le, Linux_ppc64,
  Linux_mips64, Linux_loongarch64, Linux_s390x, Linux_riscv64, Aix_ppc64,
  Darwin_x86_64, Darwin_aarch64, FreeBSD_amd64, FreeBSD_i386,
  Win_x86, Win_amd64, Win_aarch64, Cloud
)

classifiedConfigs.flatMap(c => Seq(
  c / packageBin / packageOptions += multiReleaseAttribute,
  c / packageBin / mappings := (c / packageBin / mappings).dependsOn(jniCompile, ffmApiCheck).value,
  c / packageBin := {
    val jar = (c / packageBin).value
    verifyMultiRelease(jar, streams.value.log)
    jar
  }
))

// Everything .github/scripts/RunTestsFromJar.java needs to run the suite against
// the packaged jar under an arbitrary JVM. It has to come from sbt because only
// sbt knows the dependency classpath - and it has to be *written* by sbt rather
// than scraped off `export Test/fullClasspath`, which meant parsing log output
// for the one line that looked like a path list.
//
// A properties file rather than a bare path list: Properties.store escapes the
// backslashes in Windows paths and Properties.load puts them back, so the same
// file works on every platform with no separator sniffing and no cygpath.
lazy val testFromJarSetup = taskKey[File]("Package the jar and write what RunTestsFromJar.java needs")

testFromJarSetup := {
  val log = streams.value.log
  val jar = (Compile / packageBin).value
  val out = target.value / "test-from-jar.properties"

  // The base classes must not be on the classpath: the whole point is that they
  // come out of the jar now. sbt-jacoco's own Test/fullClasspath wrapper swaps
  // target/classes for target/jacoco/instrumented-classes, so both spellings
  // have to go, or a `jacoco` run earlier in the same session would put the JNI
  // implementation back in front of the jar - green, and meaningless.
  val baseClasses = Set(
    (Compile / classDirectory).value,
    target.value / "jacoco" / "instrumented-classes"
  ).map(_.getCanonicalFile)

  val entries = (Test / fullClasspath).value.files
    .map(_.getCanonicalFile)
    .filterNot(baseClasses.contains)

  // Backstop for the filter, asked semantically rather than by path shape: no
  // surviving directory may hold a compiled com.github.luben.zstd class.
  val leaked = entries.filter(e => (e / "com" / "github" / "luben" / "zstd" / "Zstd.class").isFile)
  if (leaked.nonEmpty)
    sys.error("these classpath entries would shadow the jar's own classes:\n" +
              leaked.map("  " + _).mkString("\n"))

  val props = new java.util.Properties
  props.setProperty("jar", jar.getPath)
  props.setProperty("testClasses", (Test / classDirectory).value.getPath)
  props.setProperty("classpath", entries.map(_.getPath).mkString(java.io.File.pathSeparator))

  IO.createDirectory(out.getParentFile)
  val w = new java.io.FileOutputStream(out)
  try props.store(w, "Written by `sbt testFromJarSetup`; consumed by .github/scripts/RunTestsFromJar.java")
  finally w.close()

  log.info(s"Wrote $out (${entries.size} classpath entries)")
  out
}

// So CI can multi-release-check every published jar, not just the main one.
// Deliberately not `packagedArtifacts`: that would also run aarTask, which needs
// the Android SDK. The classified jars whose native library was built elsewhere
// come out without it, which does not affect what the check looks at.
lazy val packageClassified = taskKey[Seq[File]]("Package every classified jar")
packageClassified := packageBin.all(
  ScopeFilter(configurations = inConfigurations(classifiedConfigs: _*))
).value
