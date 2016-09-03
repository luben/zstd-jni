package com.github.luben.zstd

import org.scalatest.FlatSpec
import org.scalatest.prop.Checkers
import org.scalacheck.Arbitrary._
import org.scalacheck.Prop._
import java.io._
import scala.io._
import scala.collection.mutable.WrappedArray

class ZstdSpec extends FlatSpec with Checkers {
  implicit override val generatorDrivenConfig =
    PropertyCheckConfig(minSize = 0, maxSize = 130 * 1024)

  val levels = List(1,3,6,9,16)

  for (level <- levels) {
    "Zstd" should s"should round-trip compression/decompression at level $level" in {
      check { input: Array[Byte] =>
        {
          val size        = input.length
          val compressed  = Zstd.compress(input, level)
          val decompressed= Zstd.decompress(compressed, size)
          input.toSeq == decompressed.toSeq
        }
      }
    }
  }

  for (level <- levels) {
    "Zstd" should s"should round-trip using streaming API at level $level" in {
      check { input: Array[Byte] =>
        val size  = input.length
        val os    = new ByteArrayOutputStream(Zstd.compressBound(size.toLong).toInt)
        val zos   = new ZstdOutputStream(os, level)
        val block = 128 * 1024
        var ptr   = 0
        while (ptr < size) {
          val chunk = if (size - ptr > block) block else size - ptr
          zos.write(input, ptr, chunk)
          ptr += chunk
        }
        zos.close
        val compressed = os.toByteArray
        // now decompress
        val is    = new ByteArrayInputStream(compressed)
        val zis   = new ZstdInputStream(is)
        val output= Array.fill[Byte](size)(0)
        ptr       = 0

        while (ptr < size) {
          val chunk = if (size - ptr > block) block else size - ptr
          zis.read(output, ptr, chunk)
          ptr += chunk
        }
        zis.close
        if (input.toSeq != output.toSeq) {
          println(s"AT SIZE $size")
          println(input.toSeq + "!=" + output.toSeq)
          println("COMPRESSED: " + compressed.toSeq)
        }
        input.toSeq == output.toSeq
      }
    }
  }

  /*
  for (level <- levels) {
    "Zstd" should s"should round-trip using streaming API with unfinished chunks at level $level" in {
      check { input: Array[Byte] =>
        val size  = input.length
        val os    = new ByteArrayOutputStream(Zstd.compressBound(size.toLong).toInt)
        val zos   = new ZstdOutputStream(os, level)
        val block = 128 * 1024
        var ptr   = 0
        while (ptr < size) {
          val chunk = if (size - ptr > block) block else size - ptr
          zos.write(input, ptr, chunk)
          ptr += chunk
        }
        zos.close
        val compressed = os.toByteArray
        // now decompress
        val is    = new ByteArrayInputStream(compressed)
        val zis   = new ZstdContinuousInputStream(is)
        val output= Array.fill[Byte](size)(0)
        ptr       = 0

        while (ptr < size) {
          val chunk = if (size - ptr > block) block else size - ptr
          zis.read(output, ptr, chunk)
          ptr += chunk
        }
        zis.close
        if (input.toSeq != output.toSeq) {
          println(s"AT SIZE $size")
          println(input.toSeq + "!=" + output.toSeq)
          println("COMPRESSED: " + compressed.toSeq)
        }
        input.toSeq == output.toSeq
      }
    }
  }
  */

  for (level <- levels)
    "ZstdInputStream" should s"be able to consume files compressed by the zstd binary at level $level" in {
      val orig = new File("src/test/resources/xml")
      val file = new File(s"src/test/resources/xml-$level.zst")
      val fis  = new FileInputStream(file)
      val zis  = new ZstdInputStream(fis)
      assert(!zis.markSupported)
      assert(zis.available == 0)
      assert(zis.skip(0) == 0)
      val length = orig.length.toInt
      val buff = Array.fill[Byte](length)(0)
      var pos  = 0;
      while (pos < length) {
        pos += zis.read(buff, pos, length - pos)
      }

      val original = Source.fromFile(orig)(Codec.ISO8859).map{char => char.toByte}.to[WrappedArray]
      if(original != buff.toSeq)
        sys.error(s"Failed")
    }

  "ZstdInputStream" should s"be able to consume 2 frames in a file compressed by the zstd binary" in {
    val orig = new File("src/test/resources/xmlx2")
    val file = new File(s"src/test/resources/xml-1x2.zst")
    val fis  = new FileInputStream(file)
    val zis  = new ZstdInputStream(fis)
    assert(!zis.markSupported)
    assert(zis.available == 0)
    assert(zis.skip(0) == 0)
    val length = orig.length.toInt
    val buff = Array.fill[Byte](length)(0)
    var pos  = 0;
    while (pos < length) {
      pos += zis.read(buff, pos, length - pos)
    }

    val original = Source.fromFile(orig)(Codec.ISO8859).map{char => char.toByte}.to[WrappedArray]
    if(original != buff.toSeq)
      sys.error(s"Failed")
  }

  for (level <- levels)
    "ZstdOutputStream" should s"produce the same compressed file as zstd binary at level $level" in {
      val file = new File("src/test/resources/xml")
      val length = file.length.toInt
      val fis  = new FileInputStream(file)
      val buff = Array.fill[Byte](length)(0)
      var pos  = 0
      while( pos < length) {
        pos += fis.read(buff, pos, length - pos)
      }

      val os  = new ByteArrayOutputStream(Zstd.compressBound(file.length).toInt)
      val zos = new ZstdOutputStream(os, level)
      zos.write(buff(0).toInt)
      zos.write(buff, 1, length - 1)
      zos.close

      val compressed = os.toByteArray.toSeq
      val zst = Source.fromFile(s"src/test/resources/xml-$level.zst")(Codec.ISO8859).map{char => char.toByte}.to[WrappedArray]

     if (zst != compressed) {
        sys.error(s"Failed original ${zst.length} != ${compressed.length} result")
      }
    }

  /*

  for (version <- List("04", "05", "06", "07"))
    "ZstdInputStream" should s"be able to consume files compressed by the zstd binary version $version" in {
      val orig = new File("src/test/resources/xml")
      val file = new File(s"src/test/resources/xml_v$version.zst")
      val fis  = new FileInputStream(file)
      val zis  = new ZstdInputStream(fis)
      assert(!zis.markSupported)
      assert(zis.available == 0)
      assert(zis.skip(0) == 0)
      val length = orig.length.toInt
      val buff = Array.fill[Byte](length)(0)
      buff(0)  = zis.read().toByte
      var pos  = 1;
      while (pos < length) {
        pos += zis.read(buff, pos, length - pos)
      }

      val original = Source.fromFile(orig)(Codec.ISO8859).map{char => char.toByte}.to[WrappedArray]
      if(original != buff.toSeq)
        sys.error(s"Failed")
    }

  for (version <- List("04", "05", "06", "07"))
    "ZstdContinuousInputStream" should s"be able to consume files compressed by the zstd binary version $version" in {
      val orig = new File("src/test/resources/xml")
      val file = new File(s"src/test/resources/xml_v$version.zst")
      val fis  = new FileInputStream(file)
      val zis  = new ZstdContinuousInputStream(fis)
      assert(!zis.markSupported)
      assert(zis.available == 0)
      assert(zis.skip(0) == 0)
      val length = orig.length.toInt
      val buff = Array.fill[Byte](length)(0)
      buff(0)  = zis.read().toByte
      var pos  = 1;
      while (pos < length) {
        pos += zis.read(buff, pos, length - pos)
      }

      val original = Source.fromFile(orig)(Codec.ISO8859).map{char => char.toByte}.to[WrappedArray]
      if(original != buff.toSeq)
        sys.error(s"Failed")
    }
    */
}
