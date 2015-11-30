Zstd-jni
========

JNI bindings for **Zstd** native library that provides fast and high
compression lossless algorythm for Java and all JVM languages:

* static compress/decompress methods

* implementation of InputStream and OutputStream for transparent compression
of data streams compatible with the "zstd" program.

* minimal performance overhead

Zstd
----

**Zstd**, short for Zstandard, is a new lossless compression algorithm, which
provides both good compression ratio _and_ speed for your standard compression
needs. "Standard" translates into everyday situations which neither look for
highest possible ratio (which LZMA and ZPAQ cover) nor extreme speeds (which
LZ4 covers).

**Zstd** is developed by Yann Collet and the source is available at:
https://github.com/Cyan4973/zstd

The motivation for development, the algotithm used and its properties are
explained in the blog post that introduces the library:
http://fastcompression.blogspot.com/2015/01/zstd-stronger-compression-algorithm.html

Status and availability
-----------------------

**Zstd** has not yet reached "stable format" status. It doesn't guarantee yet that
its current compressed format will remain stable and supported in future versions.
During this period, it can still change to adapt new optimizations still being
investigated. "Stable Format" is projected sometimes early 2016.

That being said, the library is now fairly robust, able to withstand hazards
situations, including invalid inputs. The library reliability has been tested using
[Fuzz Testing](https://en.wikipedia.org/wiki/Fuzz_testing), with both
[internal tools](programs/fuzzer.c) and [external ones](http://lcamtuf.coredump.cx/afl).
Therefore, it seems now safe to test Zstandard even within production environments.


**Zstd-jni** will track the development of **Zstd** and is currently
based on version 0.4.0 without the compatibility support for the legacy formats
(0.1, 0.2, 0.3). It supports the HighCompression modes.

**Zstd-jni** will not publish any pre-build artefacts until **Zstd** and these
bindings are deemed stable.

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

If you want to publish it to you local ivy2 repositrory:

```
 $ sbt publish-local
```

License
-------

The code for these JNI bindings is licenced under BSD license - the same as
the native **Zstd** library. See the LICENSE for full copythight and
conditions.
