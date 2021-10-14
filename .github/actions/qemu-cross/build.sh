#!/bin/bash

set -e

apt-get update
apt-get install -y gcc

SBT_CMD="test"
# On big endian platforms, exclude some tests that are affected by
# https://github.com/facebook/zstd/issues/2736 in zstd 1.5.0.
if [[ $(uname -m) == "s390x" ]]; then
  SBT_CMD="testOnly * -- -l ZstdIssue2736"
fi
./sbt -v "${SBT_CMD}"
