#!/bin/bash

set -e

apt-get update
apt-get install -y gcc

SBT_CMD="test"
./sbt -v "${SBT_CMD}"
