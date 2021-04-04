#!/bin/bash

VERSION=$(cat version)

echo "Preparing version $VERSION"

compile () {
    ARCH=$1
    OS=$2
    CC=${3:-"gcc -static-libgcc"}
    HOST="gcc-$ARCH-$OS"
    echo "Compiling for $ARCH/$OS on $HOST"
    INSTALL=target/classes/$OS/$ARCH
    rsync -r --delete jni $HOST:
    rsync -r --delete src/main/native $HOST:
    ssh $HOST 'export PATH=$HOME/bin:$PATH; '$CC' -shared -flto -DDYNAMIC_BMI2=0 -fPIC -O3 -DZSTD_LEGACY_SUPPORT=4 -DZSTD_MULTITHREAD=1 -I/usr/include -I./jni -I./native -I./native/common -I./native/legacy -std=c99 -lpthread -o libzstd-jni-'$VERSION'.so native/*.c native/legacy/*.c native/common/*.c native/compress/*.c native/decompress/*.c native/dictBuilder/*.c'
    mkdir -p $INSTALL
    scp $HOST:libzstd-jni-$VERSION.so $INSTALL
}

compile_ppc64_aix () {
    ARCH=ppc64
    OS=aix
    CC=xlc_r
    HOST="gcc-$ARCH-$OS"
    echo "Compiling for $ARCH/$OS on $HOST"
    INSTALL=target/classes/$OS/$ARCH
    ssh $HOST rm -rf jni native
    scp -r jni $HOST:
    scp -r src/main/native $HOST:
    ssh $HOST 'export PATH=$HOME/bin:$PATH; '$CC' -q64 -bshared -brtl -G -DDYNAMIC_BMI2=0 -O3 -DZSTD_LEGACY_SUPPORT=4 -DZSTD_MULTITHREAD=1 -I/usr/include -I./jni -I./native -I./native/common -I./native/legacy -o libzstd-jni-'$VERSION'.so native/*.c native/legacy/*.c native/common/*.c native/compress/*.c native/decompress/*.c native/dictBuilder/*.c'
    mkdir -p $INSTALL
    scp $HOST:libzstd-jni-$VERSION.so $INSTALL
}

compile amd64   linux
compile i386    linux "gcc -march=i586 -static-libgcc"
compile ppc64   linux
compile ppc64le linux
compile aarch64 linux
compile mips64  linux
compile amd64   freebsd "cc"
compile i386    freebsd "cc -m32 -march=i486 -mfancy-math-387"
compile_ppc64_aix
