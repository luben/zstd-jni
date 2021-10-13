#!/bin/bash

set -e

apt-get update
apt-get install -y gcc
./sbt -v test package
