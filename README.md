Zstd-JNI
========

JNI bindings for **Zstd** native library that provides fast and high
compression lossless algorythm for Java and all JVM languages:

* static compress/decompress methods

* implementation of InputStream and OutputStream for transparent compression
of data streams compatible with the "zstd" program.

The code for these JNI bindings is licenced under BSD license - the same as
the native **Zstd** library.

Example performance on my laptop (i7-4558U):

```
      Uncompressable data
      --
      Compression:        662 MB/s
      Decompression:      8411 MB/s
      Compression Ratio:  0.99997043697019


      Highly compressable data
      --
      Compression:        2877 MB/s
      Decompression:      10976 MB/s
      Compression Ratio:  884.1281618887015


      Compressable data
      --
      Compression:        253 MB/s
      Decompression:      548 MB/s
      Compression Ratio:  5.639445830823509
```

Run the test suite to get the performance on your hardware.

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

**Zstd** is currently in early stages of development, there may be
uncompatible changes in the binary format and it is not yet ready for
production use.

**Zstd-JNI** will track the development of **Zstd** and is currently
based on version 0.0.2.

I will not publish any pre-build artefacts until **Zstd** and these
bindings are deemed production ready.

Building and dependencies
-------------------------

**Zstd-JNI** uses SBT for building the libary and running the tests.

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
