#!/bin/bash -x

VERSION=$(cat version)

compile() {
    ARCH=$1
    CC=$2

    OS=linux
    BUILD_DIR="build_cross/$ARCH"

    echo "Compiling for $ARCH/$OS"
    INSTALL=target/classes/$OS/$ARCH

    mkdir -p $BUILD_DIR
    rsync -r --delete jni $BUILD_DIR/
    rsync -r --delete src/main/native $BUILD_DIR
    rsync ./libzstd-jni.so.map $BUILD_DIR

    pushd $BUILD_DIR
    $CC -shared -static-libgcc -Wl,--version-script=./libzstd-jni.so.map -fPIC -O3 -flto -DZSTD_LEGACY_SUPPORT=4 -DZSTD_MULTITHREAD=1 -I/usr/include -I./jni -I./native -I./native/common -I./native/legacy -std=c99 -lpthread -o libzstd-jni-$VERSION.so native/*.c native/legacy/*.c native/common/*.c native/compress/*.c native/decompress/*.c native/dictBuilder/*.c
    popd

    mkdir -p $INSTALL
    cp $BUILD_DIR/libzstd-jni-$VERSION.so $INSTALL
}

compile arm arm-linux-gnueabihf-gcc
compile s390x "s390x-linux-gnu-gcc -march=z196"
#compile aarch64 aarch64-linux-gnu-gcc
#compile mips64 mips64-linux-gnu-gcc

