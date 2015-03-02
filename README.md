Zstd-JNI
========

This reporsitory contains JNI bindings for the **Zstd** native library
that expose to all JVM languages:

* static compress/decompress methods

* implementation of InputStream and OutputStream for transparent compression
of data streams compatible with the "zstd" program provided by **Zstd**.

The code for these JNI bindings is licenced under BSD license - the same as
the native library.

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

We will not publish any pre-build artefacts until **Zstd** and these
bindings are deemed production ready.

Building and dependencies
-------------------------

**Zstd-JNI** uses SBT for building the libary and running the tests.

The build system depends on Scala and the tests depend on ScalaTest and
ScalaCheck but the produced JAR does not have any dependencies. It also
embeds the native library.
