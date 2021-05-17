name := "sbt-jni"

organization := "com.github.joprice"

enablePlugins(SbtPlugin)

scalaVersion := "2.12.13"

licenses += ("Apache-2.0", url(
  "http://www.apache.org/licenses/LICENSE-2.0.html"
))

scalacOptions := Seq(
  "-unchecked",
  "-deprecation",
  "-feature",
  "-encoding",
  "utf8"
)
