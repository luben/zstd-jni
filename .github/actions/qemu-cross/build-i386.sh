#!/bin/bash

set -e

apt-get update
apt-get install -y gcc curl openjdk-25-jdk-headless

JAVA_HOME=$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")
export JAVA_HOME

# Debian's 32-bit JDK is Zero (interpreter only), so it does not recognize
# compiler flags such as -XX:MaxInlineLevel=18 from sbt's defaults. JVM_OPTS
# replaces those defaults wholesale.
export JVM_OPTS="-Xms512m -Xss2m"

pushd sbt-java-module-info
./sbt publishLocal
popd

# The 32-bit job exists to exercise FFM's FallbackLinker and 4-byte size_t.
# JNI coverage is provided by the other jobs.
./sbt -v testFromJarSetup \
         "testFromJar $JAVA_HOME"
