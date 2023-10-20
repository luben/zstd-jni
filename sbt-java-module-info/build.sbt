inThisBuild(
  Seq(
    organization := "com.sandinh",
    versionScheme := Some("early-semver"),
    developers := List(
      Developer(
        "thanhbv",
        "Bui Viet Thanh",
        "thanhbv@sandinh.net",
        url("https://sandinh.com")
      )
    ),
    licenses := Seq("Apache 2" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt")),
    scalaVersion := "2.12.18",
  )
)
val scalatestV = "3.2.16"
lazy val root = (project in file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-java-module-info",
    pluginCrossBuild / sbtVersion := "1.9.0",
    libraryDependencies ++= Seq(
      "org.jetbrains" % "annotations" % "24.0.1" % Provided,
      "org.ow2.asm" % "asm-tree" % "9.5",
      "org.scalatest" %% "scalatest-flatspec" % scalatestV % Test,
      "org.scalatest" %% "scalatest-mustmatchers" % scalatestV % Test,
    ),
    scriptedScalatestDependencies ++= Seq(
      s"org.scalatest::scalatest-flatspec:$scalatestV",
      s"org.scalatest::scalatest-mustmatchers:$scalatestV",
    ),
    Compile / doc / sources := Nil,
  )
