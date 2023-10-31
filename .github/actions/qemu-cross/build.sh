#!/bin/bash

set -e

apt-get update
apt-get install -y gcc openjdk-11-jdk

pushd sbt-java-module-info
./sbt publishLocal
popd

SBT_CMD="test"
./sbt -v "${SBT_CMD}"
