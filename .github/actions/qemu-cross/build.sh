#!/bin/bash

set -e

apt-get update
# curl fetches the sbt launcher jar. 11-jdk-focal shipped it; 25-jdk-noble has
# neither curl nor wget, and without one download_url() is a silent no-op that
# ends in "Could not download and verify the launcher".
apt-get install -y gcc curl

# The plain distro images ship no JDK. It has to be 22+ or ffmCompile skips.
if ! command -v javac > /dev/null; then
    apt-get install -y openjdk-25-jdk-headless
    JAVA_HOME=$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")
    export JAVA_HOME
    # Only the 32-bit guests land here (see the matrix in ci-qemu-cross.yml:
    # the others carry their own JDK), and Debian's 32-bit builds are Zero -
    # interpreter only, no JIT, so no compiler flags exist at all and the
    # -XX:MaxInlineLevel=18 in sbt's default_jvm_opts_common is rejected as an
    # unrecognized VM option before the launcher starts. Same defaults, minus
    # that flag; JVM_OPTS replaces them wholesale rather than adding to them.
    export JVM_OPTS="-Xms512m -Xss2m"
fi

pushd sbt-java-module-info
./sbt publishLocal
popd

# Both implementations (JNI and FFM) out of the one packaged jar.
./sbt -v testFromJarSetup \
         "testFromJar $JAVA_HOME" \
         "testFromJar $JAVA_HOME -Djdk.util.jar.enableMultiRelease=false"
