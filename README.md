Zstd-jni
========

JNI bindings for **Zstd** native library that provides fast and high
compression lossless algorythm for Java and all JVM languages:

* static compress/decompress methods

* implementation of InputStream and OutputStream for transparent compression
of data streams compatible with the "zstd" program.

Example performance on my laptop (i7-4558U):

```
      Uncompressable data
      --
      Compression:        704 MB/s
      Decompression:      10842 MB/s
      Compression Ratio:  0.99997043697019

      Highly compressable data
      --
      Compression:        2910 MB/s
      Decompression:      14079 MB/s
      Compression Ratio:  866.5917355371901

      Compressable data
      --
      Compression:        310 MB/s
      Decompression:      877 MB/s
      Compression Ratio:  5.656848147428843
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

**Zstd** has not yet reached "stable" status. Specifically, it doesn't guarantee
yet that its current compressed format will remain stable and supported in future
versions.It may still change to adapt further optimizations still being investigated.
However, the library starts to be pretty robust, able to withstand hazards situations,
including invalid input. The library reliability has been tested using
[Fuzz Testing](https://en.wikipedia.org/wiki/Fuzz_testing), using both
[internal tools](programs/fuzzer.c) and
[external ones](http://lcamtuf.coredump.cx/afl). Therefore, you can now safely test
zstd, even within production environments.

"Stable Format" is projected sometimes early 2016.

**Zstd-jni** will track the development of **Zstd** and is currently
based on version 0.2.1 without the compatibility support for the v0.1 format.

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
