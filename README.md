Zstd-jni
========

[![CI](https://github.com/luben/zstd-jni/workflows/CI/badge.svg)](https://github.com/luben/zstd-jni/actions)
[![codecov.io](http://codecov.io/github/luben/zstd-jni/coverage.svg?branch=master)](http://codecov.io/github/luben/zstd-jni?branch=master)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.luben/zstd-jni.svg?label=Maven%20Central)](https://search.maven.org/artifact/com.github.luben/zstd-jni/)
[![Javadocs](https://www.javadoc.io/badge/com.github.luben/zstd-jni.svg)](https://www.javadoc.io/doc/com.github.luben/zstd-jni)

Overview
--------

JNI bindings for **Zstd** native library that provides fast and high
compression lossless algorithm for Android, Java and all JVM languages:

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

The motivation for development, the algorithm used and its properties are
explained in the blog post that introduces the library:
http://fastcompression.blogspot.com/2015/01/zstd-stronger-compression-algorithm.html

Status and availability
-----------------------

**Zstd** is production ready with a stable format.

**Zstd-jni** is tracking the release branch of **Zstd** (master) with
compatibility support for the legacy formats (since version 0.4).

**Zstd-jni** version uses the base **Zstd** version with **Zstd-jni** release
appended with a dash, e.g. "1.2.0-2" is the second **Zstd-jni** release based
on **Zstd** version 1.2.0.

Limitations
-----------
The Java classes cannot be renamed/minimized/relocated. JVM linking the native
library depends on the class name that is trying to link the native part, so
changing the class names will lead to failed linking at runtime.

Building and dependencies
-------------------------

**Zstd-jni** uses SBT for building the library and running the tests.

The build system depends on Scala and the tests depend on ScalaTest and
ScalaCheck but the produced JAR does not have any dependencies. It also
embeds the native library.

*Note*: For the moment the project depends on a local build of `sbt-java-module-info`
v0.5.2, as that version is not published to Maven. Before compiling, you need to publish it locally:
```
$ cd sbt-java-module-info && ./sbt publishLocal && cd -
```

Create package:
```
 $ ./sbt package
```

Insatll package locally into maven repository:
```
 $ ./install-jar.sh
```

License
-------

The code for these JNI bindings is licenced under 2-clause BSD license.
The native **Zstd** library is licensed under 3-clause BSD license or
GPL2. See the LICENSE file and LICENSE and COPYRIGHT in src/main/native
for full copyright and conditions.
