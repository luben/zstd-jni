#!/bin/bash

# Define the directory and file pattern
TARGET_DIR="./target"
FILE_PATTERN="zstd-jni-*.jar"

# Find the jar file matching the pattern
JAR_FILE=$(find "$TARGET_DIR" -name "$FILE_PATTERN" -print -quit)

# Check if the jar file exists
if [[ -z "$JAR_FILE" ]]; then
  echo "Error: No jar file matching the pattern '$FILE_PATTERN' found in '$TARGET_DIR'."
  exit 1
fi

# Extract the version from the jar file name
VERSION=$(basename "$JAR_FILE" | sed -n 's/^zstd-jni-\(.*\)\.jar$/\1/p')

# Check if version was extracted successfully
if [[ -z "$VERSION" ]]; then
  echo "Error: Unable to extract version from jar file name '$JAR_FILE'."
  exit 1
fi

# Run the Maven install command with the extracted version
echo "Installing zstd-jni library to the local maven repository"
mvn install:install-file -Dfile="$JAR_FILE" -DgroupId=com.carrotdata -DartifactId=zstd-jni -Dversion="$VERSION" -Dpackaging=jar
