resolvers += Resolver.url("joprice-sbt-plugins", url("http://dl.bintray.com/content/joprice/sbt-plugins"))(Resolver.ivyStylePatterns)

addSbtPlugin("com.github.joprice" % "sbt-jni" % "0.1.2")
addSbtPlugin("com.typesafe.sbt" % "sbt-osgi" % "0.8.0")
addSbtPlugin("de.johoop" % "jacoco4sbt" % "2.1.6")
