#!/bin/bash

set -e

apt-get update
# curl fetches the sbt launcher jar. The JDK image has neither curl nor wget,
# and without one download_url() is a silent no-op that ends in
# "Could not download and verify the launcher".
apt-get install -y gcc curl

pushd sbt-java-module-info
./sbt publishLocal
popd

# Exercise both implementations from the same packaged jar.
./sbt -v compile testFromJarSetup \
         "testFromJar $JAVA_HOME" \
         "testFromJar $JAVA_HOME -Djdk.util.jar.enableMultiRelease=false"
