#!/bin/bash

# Determine the OS type
case "$(uname -s)" in
    Linux)
        OS_TYPE="linux"
        ;;
    Darwin)
        OS_TYPE="darwin"
        ;;
    MINGW32_NT* | MINGW64_NT*)
        OS_TYPE="win"
        ;;
    *)
        echo "Unsupported OS"
        exit 1
        ;;
esac

# Determine the architecture
case "$(uname -m)" in
    x86_64)
        ARCH_TYPE="amd64"
        ;;
    aarch64)
        ARCH_TYPE="aarch64"
        ;;
    i386 | i686)
        ARCH_TYPE="i386"
        ;;
    arm)
        ARCH_TYPE="arm"
        ;;
    arm64)
        ARCH_TYPE="aarch64"
        ;;
    ppc64)
        ARCH_TYPE="ppc64"
        ;;
    mips64)
        ARCH_TYPE="mips64"
        ;;
    s390x)
        ARCH_TYPE="s390x"
        ;;
    riscv64)
        ARCH_TYPE="riscv64"
        ;;
    loongarch64)
        ARCH_TYPE="loongarch64"
        ;;
    *)
        echo "Unsupported architecture"
        exit 1
        ;;
esac

echo Build for:
echo OS_TYPE=$OS_TYPE
echo ARCH_TYPE=$ARCH_TYPE

# Define the directory and file pattern
TARGET_DIR="./target"
FILE_PATTERN="zstd-jni-*"

# Find the jar file matching the pattern
JAR_FILE=$(find "$TARGET_DIR" | grep ${FILE_PATTERN} | grep ${OS_TYPE} | grep ${ARCH_TYPE} | grep "\.jar")
echo OS-Specific Artifact: $JAR_FILE

# Check if the jar file exists
if [[ -z "$JAR_FILE" ]]; then
  echo "Error: No jar file matching the pattern '$FILE_PATTERN' found in '$TARGET_DIR'."
  exit 1
fi

# Extract the version from the jar file name
VERSION=$(echo "$JAR_FILE" | sed -E 's/.*-([0-9]+\.[0-9]+\.[0-9]+-[0-9]+)-[^-]+\.jar/\1/')

# Check if version was extracted successfully
if [[ -z "$VERSION" ]]; then
  echo "Error: Unable to extract version from jar file name '$JAR_FILE'."
  exit 1
fi
echo VERSION=$VERSION

# Run the Maven install command with the extracted version
echo "Installing zstd-jni library to the local maven repository"
mvn install:install-file -Dfile="$JAR_FILE" -DgroupId=com.carrotdata -DartifactId=zstd-jni -Dversion="$VERSION" -Dpackaging=jar
