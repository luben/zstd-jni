import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers
import com.sandinh.javamodule.moduleinfo.*
import com.sandinh.javamodule.moduleinfo.Utils.ModuleIDOps

inThisBuild(
  Seq(
    scalaVersion := "3.3.0",
    organization := "com.sandinh",
  )
)
Global / moduleInfos := Seq(
  AutomaticModule("paranamer", "com.thoughtworks.paranamer:paranamer"),
  JpmsModule(
    "com.fasterxml.jackson.scala",
    "com.fasterxml.jackson.module:jackson-module-scala_3",
  ),
)
// we use `sub/moduleInfo` to dynamically calculate `moduleInfos` for `root` project
// but ModuleInfoPlugin is not enabled in `sub` so sbt will warn `sub / moduleInfo` is unused
// In real projects, we should enable ModuleInfoPlugin to make our project jpms compatible
Global / excludeLintKeys += moduleInfo
lazy val sub = project
//  .enablePlugins(ModuleInfoPlugin)
  .settings(
    moduleInfo := AutomaticModule("sd.test.sub"),
  )
lazy val root = project
  .in(file("."))
  .dependsOn(sub)
  .enablePlugins(ModuleInfoPlugin)
  .settings(
    libraryDependencies ++= Seq(
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.15.2",
      "org.jetbrains" % "annotations" % "24.0.1" % Provided,
    ),
    moduleInfo := JpmsModule("sd.test.root"),
    scriptedScalatestSpec := Some(new AnyFlatSpec with Matchers with ScriptedScalatestSuiteMixin {
      override val sbtState: State = state.value

      "ModuleInfoPlugin" should "generate module-info.class" in {
        val Some((_, Value(ret))) = Project.runTask(Compile / products, sbtState)
        val f = ret.flatMap(IO.listFiles(_, "module-info.class")).headOption
        assert(f.isDefined)
        assert(f.get.getParentFile.name == "classes")
      }
    }),
  )
