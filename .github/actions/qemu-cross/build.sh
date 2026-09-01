#!/bin/bash

set -e

apt-get update
apt-get install -y gcc

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
