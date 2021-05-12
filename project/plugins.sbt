import java.nio.file.Paths
resolvers += Resolver.url("joprice-sbt-plugins", url("file:///" + Paths.get(".").toAbsolutePath + "/project/ivy2"))(Resolver.ivyStylePatterns)
addSbtPlugin("com.github.joprice" % "sbt-jni" % "0.2.2-SNAPSHOT")
addSbtPlugin("com.typesafe.sbt" % "sbt-osgi" % "0.9.4")
addSbtPlugin("com.github.sbt" % "sbt-jacoco" % "3.1.0")
