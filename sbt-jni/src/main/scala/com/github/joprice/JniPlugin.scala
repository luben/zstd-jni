package com.github.joprice

import sbt._
import Keys._

import scala.language.postfixOps
import java.io.File

import plugins.JvmPlugin

import scala.sys.process.Process

object JniPlugin extends AutoPlugin { self =>

  override def requires = JvmPlugin

  object autoImport {
    lazy val JniPlugin = self

    //tasks

    lazy val jni = taskKey[Unit]("Run jni build")

    lazy val jniCompile = taskKey[Unit]("Compiles jni sources using gcc")

    lazy val jniJavah = taskKey[Unit]("Builds jni sources")

    lazy val jniPossibleNativeSources = taskKey[Seq[File]](
      "Lists files with native annotations. Used to find classes to append to 'nativeSource'"
    )

    // settings

    lazy val jniNativeCompiler =
      settingKey[String]("Compiler to use. Defaults to gcc")

    lazy val jniCppExtensions =
      settingKey[Seq[String]]("Extensions of source files")

    lazy val jniNativeClasses =
      settingKey[Seq[String]]("Classes with native methods")

    lazy val jniHeadersPath = settingKey[File]("Generated JNI headers")

    lazy val jniBinPath = settingKey[File]("Shared libraries produced by JNI")

    lazy val jniNativeSources = settingKey[File]("JNI native sources")

    lazy val jniIncludes = settingKey[Seq[String]]("Compiler includes settings")

    lazy val jniLibraries =
      settingKey[Seq[String]]("Compiler libraries settings")

    lazy val jniSourceFiles = settingKey[Seq[File]]("Jni source files")

    lazy val jniGccFlags = settingKey[Seq[String]]("Flags to be passed to gcc")

    lazy val jniLibraryName =
      settingKey[String]("Shared library produced by JNI")

    lazy val jniJreIncludes = settingKey[Seq[String]]("Includes for jni")

    lazy val jniJdkHome =
      settingKey[Option[File]]("Used to find jre include files for JNI")

    //TODO: replace with generic flags?
    lazy val jniUseCpp11 =
      settingKey[Boolean]("Whether to pass the cpp11 flag to the compiler")

    lazy val jniGenerateHeaders =
      settingKey[Boolean]("Whether to generate C headers using javah")

    lazy val jniLibSuffix =
      settingKey[String]("Suffix for shared library, e.g., .so, .dylib")
  }

  import autoImport._

  def jreIncludeFolder =
    System
      .getProperty("os.name")
      .toLowerCase
      .replace(' ', '_')
      .replace('.', '_') match {
      case os if os.contains("mac")   => "darwin"
      case os if os.contains("linux") => "linux"
      case os if os.contains("win")   => "win32"
      case _ =>
        throw new Exception(
          "Cannot determine os name. Provide a value for `javaInclude`."
        )
    }

  def withExtensions(directory: File, extensions: Seq[String]): Seq[File] = {
    val extensionsFilter =
      extensions.map("*." + _).foldLeft(NothingFilter: FileFilter)(_ || _)
    (directory ** extensionsFilter).get
  }

  override lazy val projectSettings = Seq(
    jniNativeCompiler := "gcc",
    jniNativeClasses := Seq.empty,
    jniJdkHome := {
      val home = new File(System.getProperty("java.home"))
      if (home.exists) Some(home) else None
    },
    jniJreIncludes := {
      jniJdkHome.value.fold(Seq.empty[String]) { home =>
        val absHome = home.getAbsolutePath
        // in a typical installation, jdk files are one directory above the location of the jre set in 'java.home'
        Seq(s"include", s"include/$jreIncludeFolder").map(file =>
          s"-I$absHome/../$file"
        )
      }
    },
    jniIncludes := (if (jniGenerateHeaders.value)
                      Seq(
                        s"-I${jniHeadersPath.value}"
                      )
                    else Seq()) ++ jniJreIncludes.value,
    jniLibraries := Seq.empty,
    jniUseCpp11 := true,
    jniGenerateHeaders := true,
    // 'dylib' and 'jnilib' work on mac, while linux expects 'so'
    jniLibSuffix := "jnilib",
    jniGccFlags := Seq(
      "-shared",
      "-fPIC",
      "-O3"
    ) ++ (if (jniUseCpp11.value) Seq("-std=c++0x") else Seq.empty)
      ++ jniIncludes.value,
    jniBinPath := (target in Compile).value / "native" / "bin",
    jniHeadersPath := (target in Compile).value / "native" / "include",
    jniNativeSources := sourceDirectory.value / "main" / "native",
    jniCppExtensions := Seq("c", "cpp", "cc", "cxx"),
    jniSourceFiles := withExtensions(
      jniNativeSources.value,
      jniCppExtensions.value
    ),
    jniCompile := Def
      .task {
        val log = streams.value.log
        jniBinPath.value.getAbsoluteFile.mkdirs()
        val flags = jniGccFlags.value.mkString(" ")
        val sources = jniSourceFiles.value.mkString(" ")
        val libs = jniLibraries.value.mkString(" ")
        val target =
          jniBinPath.value / ("lib" + jniLibraryName.value + "." + jniLibSuffix.value)
        val command =
          s"${jniNativeCompiler.value} $flags -o $target $sources $libs"
        log.info(command)
        checkExitCode(
          jniNativeCompiler.value,
          Process(command, jniBinPath.value) ! log
        )
      }
      .dependsOn(jniJavah)
      .tag(Tags.Compile, Tags.CPU)
      .value,
    jniJavah := Def.taskDyn {
      if (!jniGenerateHeaders.value)
        Def
          .task {
            val log = streams.value.log
            log.info("Generating headers is disabled.")
          }
          .dependsOn(compile in Compile)
          .tag(Tags.Compile, Tags.CPU)
      else if (!jniNativeClasses.value.isEmpty)
        Def
          .task {
            val log = streams.value.log
            val classes = (fullClasspath in Compile).value
              .map(_.data)
              .mkString(File.pathSeparator)
            val javahCommand =
              s"javah -d ${jniHeadersPath.value} -classpath $classes ${jniNativeClasses.value.mkString(" ")}"
            log.info(javahCommand)
            checkExitCode("javah", Process(javahCommand) ! log)
          }
          .dependsOn(compile in Compile)
          .tag(Tags.Compile, Tags.CPU)
      else
        Def
          .task {
            val log = streams.value.log
            log.info("jniNativeClasses is empty: skipping header generation.")
          }
          .dependsOn(compile in Compile)
          .tag(Tags.Compile, Tags.CPU)
    }.value,
    jniPossibleNativeSources := {
      def withExtension(dir: File, extension: String) =
        (dir ** s"*.$extension").filter(_.isFile).get

      val JavaRegex = " native .*\\)\\s*;".r
      val nativeJava =
        withExtension((javaSource in Compile).value, "java").flatMap { file =>
          val source = IO.readLines(file)
          val hasNative = source.exists {
            case JavaRegex(_) => true
            case _            => false
          }
          if (hasNative) Some(file) else None
        }

      val nativeScala = withExtension((scalaSource in Compile).value, "scala")
        .filter(IO.read(_).contains("@native"))

      nativeJava ++ nativeScala
    },
    cleanFiles ++= Seq(
      jniBinPath.value,
      jniHeadersPath.value
    ),
    compile := (compile in Compile).dependsOn(jniCompile).value,
    compile in Test := (compile in Test).dependsOn(jniCompile).value,
    // Make shared lib available at runtime. Must be used with forked jvm to work.
    javaOptions ++= Seq(
      s"-Djava.library.path=${jniBinPath.value}"
    ),
    //required in order to have a separate jvm to set java options
    fork in run := true,
    fork in test := true
  )

  private def checkExitCode(name: String, exitCode: Int): Unit =
    if (exitCode != 0)
      throw new MessageOnlyException(
        s"$name exited with non-zero status ($exitCode)"
      )
}
