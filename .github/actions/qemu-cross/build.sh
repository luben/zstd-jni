#!/bin/bash

set -e

apt-get update
# curl fetches the sbt launcher jar. 11-jdk-focal shipped it; 25-jdk-noble has
# neither curl nor wget, and without one download_url() is a silent no-op that
# ends in "Could not download and verify the launcher".
apt-get install -y gcc curl

pushd sbt-java-module-info
./sbt publishLocal
popd

# Both implementations (JNI and FFM) out of the one packaged jar.
./sbt -v testFromJarSetup \
         "testFromJar $JAVA_HOME" \
         "testFromJar $JAVA_HOME -Djdk.util.jar.enableMultiRelease=false"
