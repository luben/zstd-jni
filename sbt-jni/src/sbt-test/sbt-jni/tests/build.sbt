
enablePlugins(JniPlugin)

scalaVersion := "2.12.12"

jniNativeClasses := Seq(
  "com.joprice.Basic"
)

jniNativeCompiler := "g++"

jniLibraryName := "basic"

jniLibSuffix := (System.getProperty("os.name").toLowerCase.replace(' ', '_').replace('.', '_') match {
  case os if os.contains("mac")   => "dylib"
  case os if os.contains("win")   => "dll"
  case _  => "so"
})

TaskKey[Unit]("check") := {
  val files = jniSourceFiles.value
  require(files.length == 1, "missing source files")
  require(files.head.getName.endsWith("basic.cpp"))
  require((jniBinPath.value / s"lib${jniLibraryName.value}.${jniLibSuffix.value}").exists)
}
