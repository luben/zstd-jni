#!/bin/bash

set -e

apt-get update
# curl is what the sbt bootstrap script uses to fetch its launcher jar. The old
# 11-jdk-focal image shipped it; eclipse-temurin:25-jdk-noble installs neither
# curl nor wget (its wget is a transient build dep, purged in the same layer).
# Without one of them download_url() is a silent no-op and sbt dies with
# "Could not download and verify the launcher".
apt-get install -y gcc curl

pushd sbt-java-module-info
./sbt publishLocal
popd

# Packages the jar - which is where the Multi-Release dispatch check runs, so a
# green FFM run below cannot mean "resolved to the base class because there was
# nothing else to resolve to" - and writes the classpath the runs need.
./sbt -v testFromJarSetup

# Both implementations out of the one packaged jar, exactly as on the other
# platforms. The image ships JDK 25 alone, so unlike the native runners there is
# no JDK 11 leg here; the 8-21 floor is covered there.
java .github/scripts/RunTestsFromJar.java "$JAVA_HOME"
java .github/scripts/RunTestsFromJar.java "$JAVA_HOME" -Djdk.util.jar.enableMultiRelease=false
