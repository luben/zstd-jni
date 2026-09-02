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

# The same suite against both implementations. -Dzstd.ffm=true makes sbt prepend
# target/classes/META-INF/versions/22 to the test classpath, so the versioned
# classes shadow their base counterparts. It has to be a second sbt process: the
# property is read while build.sbt is evaluated, and a JNI library can only be
# System.load-ed by one classloader per JVM.
./sbt -v test
./sbt -v -Dzstd.ffm=true test
