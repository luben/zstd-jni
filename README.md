Zstd-jni
========

[![Build Status](https://api.travis-ci.org/luben/zstd-jni.svg)](https://travis-ci.org/luben/zstd-jni)
[![Windows Build](https://ci.appveyor.com/api/projects/status/qs6j4n9t3bsi5p4w/branch/master?svg=true)](https://ci.appveyor.com/project/luben/zstd-jni)
[![codecov.io](http://codecov.io/github/luben/zstd-jni/coverage.svg?branch=master)](http://codecov.io/github/luben/zstd-jni?branch=master)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.luben/zstd-jni.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.luben/zstd-jni)

Overview
--------

JNI bindings for **Zstd** native library that provides fast and high
compression lossless algorithm for Java and all JVM languages:

* static compress/decompress methods

* implementation of InputStream and OutputStream for transparent compression
of data streams fully compatible with the "zstd" program.

* minimal performance overhead

Zstd
----

**Zstd**, short for Zstandard, is a new lossless compression algorithm, which
provides both good compression ratio _and_ speed for your standard compression
needs. "Standard" translates into everyday situations which neither look for
highest possible ratio (which LZMA and ZPAQ cover) nor extreme speeds (which
LZ4 covers).

**Zstd** is developed by Yann Collet and the source is available at:
https://github.com/facebook/zstd

The motivation for development, the algotithm used and its properties are
explained in the blog post that introduces the library:
http://fastcompression.blogspot.com/2015/01/zstd-stronger-compression-algorithm.html

Status and availability
-----------------------

**Zstd** is production ready with a stable format.

**Zstd-jni** is tracking the release branch of **Zstd** (master) with
compatibility support for the legacy formats (since version 0.4).

Building and dependencies
-------------------------

**Zstd-jni** uses SBT for building the libary and running the tests.

The build system depends on Scala and the tests depend on ScalaTest and
ScalaCheck but the produced JAR does not have any dependencies. It also
embeds the native library.

How to build:

```
 $ sbt compile test package
```

If you want to publish it to you local ivy2 repository:

```
 $ sbt publish-local
```

Binary releases
---------------

The binary releases are architecture dependent because we are embedding the
native library in the provided Jar file. Currently they are built for *win-amd64*, *win-x86*,
*linux-amd64*, *linux-i386*, *linux-aarch64*, *linux-ppc64*, *aix-ppc64*
and *max_os_x-x86_64*. More builds will be available if I get access to more
platforms.

You can find published releases on Maven Central.

    <dependency>
        <groupId>com.github.luben</groupId>
        <artifactId>zstd-jni</artifactId>
        <version>1.2.0</version>
    </dependency>

sbt dependency:

    libraryDependencies += "com.github.luben" % "zstd-jni" % "1.2.0"

Link for direct download if you don't use a dependency manager:

 - https://repo1.maven.org/maven2/com/github/luben/zstd-jni/

If there is not yet a binary release compatible with your platform look how
to build it locally under the [Building](#building-and-dependencies) section.

License
-------

The code for these JNI bindings is licenced under 2-clause BSD license.
The native **Zstd** library is licensed under 3-clause BSD license with
a patent grant. See the LICENSE and src/main/native/LICENSE for full
copyright and conditions.
