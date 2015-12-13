resolvers += Resolver.url("joprice maven", url("http://dl.bintray.com/content/joprice/maven"))(Resolver.ivyStylePatterns)

addSbtPlugin("com.github.joprice" % "sbt-jni" % "0.1.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-osgi" % "0.7.0")
addSbtPlugin("de.johoop" % "jacoco4sbt" % "2.1.6")
