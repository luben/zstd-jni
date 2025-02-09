#!/bin/bash -x

VERSION=$(cat version)

compile() {
    ARCH=$1
    CC=$2

    OS=linux
    BUILD_DIR="build_cross/$ARCH"

    echo "Compiling for $ARCH/$OS"
    INSTALL=target/classes/$OS/$ARCH

    ssh buster mkdir -p $BUILD_DIR
    rsync -r --delete jni buster:$BUILD_DIR/
    rsync -r --delete src/main/native buster:$BUILD_DIR
    rsync ./libzstd-jni.so.map buster:$BUILD_DIR


    ssh buster 'cd '$BUILD_DIR'; '$CC' -shared -static-libgcc -Wl,--version-script=./libzstd-jni.so.map -Wl,-Bsymbolic -fPIC -O3 -flto -DZSTD_LEGACY_SUPPORT=4 -DZSTD_MULTITHREAD=1 -I/usr/include -I./jni -I./native -I./native/common -I./native/legacy -std=c99 -lpthread -o libzstd-jni-'$VERSION'.so native/*.c native/legacy/*.c native/common/*.c native/compress/*.c native/decompress/*.c native/dictBuilder/*.c'

    mkdir -p $INSTALL
    rsync buster:$BUILD_DIR/libzstd-jni-$VERSION.so $INSTALL
    chmod -x $INSTALL/libzstd-jni-$VERSION.so
}

compile arm "arm-linux-gnueabihf-gcc-8 -marm -march=armv6kz+fp -mfpu=vfp -mfloat-abi=hard"
compile s390x "s390x-linux-gnu-gcc-8 -march=z196"
compile mips64 mips64-linux-gnuabi64-gcc-8
compile i386 "i686-linux-gnu-gcc-8 -march=i586"
