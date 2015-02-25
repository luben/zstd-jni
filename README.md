Zstd-JNI
========

This reporsitory contains JNI bindings for the **Zstd** native library
that expose to all JVM languages:

* static compress/decompress methods

* implementation of InputStream and OutputStream for transparent compression
of data streams compatible with the "zstd" program provided by
**Zstd** library.

The code for JNI bindings is licenced under BSD license - the same as the
native library.

Zstd
----

**Zstd**, short for Zstandard, is a new lossless compression algorithm, which
provides both good compression ratio _and_ speed for your standard compression
needs. "Standard" translates into everyday situations which neither look for
highest possible ratio (which LZMA and ZPAQ cover) nor extreme speeds (which
LZ4 covers).

**Zstd** is developed by Yann Collet and the source is available at:
https://github.com/Cyan4973/zstd


Status and availability
-----------------------

**Zstd** is currently in early stages of development, there may be
uncompatible changes in the binary format and it is not yet ready for
production use.

**Zstd-JNI** is based on an experimental branch (streamAPI2) and will
track the development of **Zstd**.

We will not publish any pre-build artefacts until **Zstd** and these
bindings are deemed production ready.

Building and dependencies
-------------------------

**Zstd-JNI** uses SBT for building the libary and running the tests.

The produced JAR does not have any dependencies and embeds the native
shared library.

The build system depends on Scala and the tests depend on ScalaTest and
ScalaCheck.
