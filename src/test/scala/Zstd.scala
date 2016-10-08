package com.github.luben.zstd

import org.scalatest.FlatSpec
import org.scalatest.prop.{Checkers, Whenever}
import org.scalacheck.Arbitrary._
import org.scalacheck.Prop._
import java.io._
import java.nio.ByteBuffer

import scala.io._
import scala.collection.mutable.WrappedArray

class ZstdSpec extends FlatSpec with Checkers with Whenever {

  implicit override val generatorDrivenConfig =
    PropertyCheckConfiguration(minSize = 0, sizeRange = 130 * 1024)

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

    it should s"round-trip compression/decompression with ByteBuffers at level $level" in {
      check { input: Array[Byte] =>
        {
          val size        = input.length
          val inputBuffer = ByteBuffer.allocateDirect(size)
          inputBuffer.put(input)
          inputBuffer.rewind()
          val compressedBuffer = ByteBuffer.allocateDirect(Zstd.compressBound(size).toInt)
          val decompressedBuffer = ByteBuffer.allocateDirect(size)

          val compressedSize = Zstd.compress(compressedBuffer, inputBuffer, level)

          compressedBuffer.flip()
          val decompressedSize = Zstd.decompress(decompressedBuffer, compressedBuffer)
          assert(decompressedSize == input.length)

          inputBuffer.rewind()
          compressedBuffer.rewind()
          decompressedBuffer.flip()

          val comparison = inputBuffer.compareTo(decompressedBuffer)
          val result = comparison == 0 && Zstd.decompressedSize(compressedBuffer) == decompressedSize
          result
        }
      }
    }

    it should s"compress with a byte[] and uncompress with a ByteBuffer $level" in {
      check { input: Array[Byte] =>
        val size = input.length
        val compressed = Zstd.compress(input, level)

        val compressedBuffer = ByteBuffer.allocateDirect(Zstd.compressBound(size.toLong).toInt)
        compressedBuffer.put(compressed)
        compressedBuffer.limit(compressedBuffer.position())
        compressedBuffer.rewind()

        val decompressedBuffer = Zstd.decompress(compressedBuffer, size)
        val decompressed = new Array[Byte](size)
        decompressedBuffer.get(decompressed)
        input.toSeq == decompressed.toSeq
      }
    }

    it should s"compress with a ByteBuffer and uncompress with a byte[] $level" in {
      check { input: Array[Byte] =>
        val size        = input.length
        val inputBuffer = ByteBuffer.allocateDirect(size)
        inputBuffer.put(input)
        inputBuffer.rewind()
        val compressedBuffer  = Zstd.compress(inputBuffer, level)
        val compressed = new Array[Byte](compressedBuffer.limit() - compressedBuffer.position())
        compressedBuffer.get(compressed)

        val decompressed = Zstd.decompress(compressed, size)
        input.toSeq == decompressed.toSeq
      }
    }
  }

  it should s"honor non-zero position and limit values in ByteBuffers" in {
    check { input: Array[Byte] =>
      val size = input.length

      //The test here is to compress the input at each of the designated levels, with each new compressed version
      //being added to the same buffer as the one before it, one after the other.  Then decompress the same way.
      //This verifies that the ByteBuffer-based versions behave the way one expects, honoring and updating position
      //and limit as they go
      val inputBuffer = ByteBuffer.allocateDirect(size)
      inputBuffer.put(input)
      inputBuffer.rewind()
      val compressedBuffer = ByteBuffer.allocateDirect(Zstd.compressBound(size).toInt * levels.size)
      val decompressedBuffer = ByteBuffer.allocateDirect(size * levels.size)

      val compressedSizes = for (level <- levels) yield {
        inputBuffer.rewind()

        val oldCompressedPosition = compressedBuffer.position()
        val oldInputPosition = inputBuffer.position()

        val compressedSize = Zstd.compress(compressedBuffer, inputBuffer, level)

        assert(inputBuffer.position() == oldInputPosition + size)
        assert(compressedBuffer.position() == oldCompressedPosition + compressedSize)

        compressedSize
      }

      compressedBuffer.flip()
      for ((level, compresedSize) <- levels.zip(compressedSizes)) {
        val oldCompressedPosition = compressedBuffer.position()
        val oldDecompressedPosition = decompressedBuffer.position()

        //This isn't using the streaming mode, so zstd expects the entire contents of the buffer to be one
        //zstd compressed output.  For this test we've stacked the compressed outputs one after the other.
        //Use limit to mark where the end of this particular compresssed output can be found.
        //Note that the duplicate() call doesn't copy memory; it just makes a duplicate ByteBuffer pointing to the same
        //location in memory
        val thisChunkBuffer = compressedBuffer.duplicate()
        thisChunkBuffer.limit(thisChunkBuffer.position() + compresedSize)
        val decompressedSize = Zstd.decompress(decompressedBuffer, thisChunkBuffer)

        assert(decompressedSize == input.length)
        compressedBuffer.position(thisChunkBuffer.position)
        assert(compressedBuffer.position() == oldCompressedPosition + compresedSize)
        assert(decompressedBuffer.position() == oldDecompressedPosition + size)
      }

      //At this point the decompressedBuffer's position should equal it's limit.
      //flip it and verify it has one copy of the input for each of the levels that were compressed
      assert(decompressedBuffer.hasRemaining == false)
      decompressedBuffer.flip()

      for (level <- levels) {
        val slice = decompressedBuffer.slice()
        slice.limit(size)
        inputBuffer.rewind()

        val expected = new Array[Byte](size)
        inputBuffer.get(expected)
        val actual = new Array[Byte](size)
        slice.get(actual)

        assert(actual.toSeq == expected.toSeq)
        decompressedBuffer.position(decompressedBuffer.position() + size)
      }

      assert(decompressedBuffer.position() == levels.size * size)
      assert(!decompressedBuffer.hasRemaining)
      true
    }
  }

  it should "fail to compress when the destination buffer is too small" in {
    check{ input: Array[Byte] =>
      (input.length > 0) ==> {
        val size = input.length
        val compressedSize = Zstd.compressBound(size.toLong)
        val compressedBuffer = ByteBuffer.allocateDirect(compressedSize.toInt / 2)
        val inputBuffer = ByteBuffer.allocateDirect(size)
        inputBuffer.put(input)
        inputBuffer.rewind()

        val e = intercept[RuntimeException] {
          Zstd.compress(compressedBuffer, inputBuffer, 1)
        }

        e.getMessage().contains("Destination buffer is too small")
      }
    }
  }

  it should "fail to decompress when the destination buffer is too small" in {
    check { input: Array[Byte] =>
      (input.length > 0) ==> {
        val size = input.length
        val compressedSize = Zstd.compressBound(size.toLong)
        val inputBuffer = ByteBuffer.allocateDirect(size)
        inputBuffer.put(input)
        inputBuffer.rewind()
        val compressedBuffer = Zstd.compress(inputBuffer, 1)
        val decompressedBuffer = ByteBuffer.allocateDirect(size - 1)

        val e = intercept[RuntimeException] {
          Zstd.decompress(decompressedBuffer, compressedBuffer)
        }

        e.getMessage().contains("Destination buffer is too small")
      }
    }
  }

  for (level <- levels) {
    "ZstdInputStream" should s"should round-trip compression/decompression at level $level" in {
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

  for (level <- levels) {
    "ZstdInputStream in continuous mode" should s"should round-trip using streaming API with unfinished chunks at level $level" in {
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
        val zis   = new ZstdInputStream(is).setContinuous(true);
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

  for (level <- levels) // the worst case
    "ZstdInputStream" should s"be able to consume 1 byte at a time files compressed by the zstd binary at level $level" in {
      val orig = new File("src/test/resources/xml")
      val file = new File(s"src/test/resources/xml-$level.zst")
      val fis  = new FileInputStream(file)
      val zis  = new ZstdInputStream(fis)
      val length = orig.length.toInt
      val buff = Array.fill[Byte](length)(0)
      var pos  = 0
      val block = 1
      while (pos < length) {
        val remain = length - pos;
        val toRead = if (remain > block) block else remain
        pos += zis.read(buff, pos, toRead)
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
      var pos  = 0;
      while (pos < length) {
        pos += zis.read(buff, pos, length - pos)
      }

      val original = Source.fromFile(orig)(Codec.ISO8859).map{char => char.toByte}.to[WrappedArray]
      if(original != buff.toSeq)
        sys.error(s"Failed")
    }

  for (version <- List("04", "05", "06", "07"))
    "ZstdInputStream in continuous mode" should s"be able to consume files compressed by the zstd binary version $version" in {
      val orig = new File("src/test/resources/xml")
      val file = new File(s"src/test/resources/xml_v$version.zst")
      val fis  = new FileInputStream(file)
      val zis  = new ZstdInputStream(fis).setContinuous(true);
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
}
