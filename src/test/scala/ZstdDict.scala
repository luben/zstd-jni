package com.github.luben.zstd

import org.scalatest.FlatSpec
import org.scalatest.prop.Checkers
import org.scalacheck.Arbitrary._
import org.scalacheck.Prop._
import java.io._
import java.nio._
import java.nio.channels.FileChannel
import java.nio.channels.FileChannel.MapMode
import java.nio.file.StandardOpenOption

import scala.io._
import scala.collection.mutable.WrappedArray

class ZstdDictSpec extends FlatSpec with Checkers {

  implicit override val generatorDrivenConfig =
    PropertyCheckConfiguration(minSize = 0, sizeRange = 130 * 1024)

  val levels = List(1)
  val legacySettings = List(true, false)
  //val levels = List(1,3,6,9,16)


  def trainDictDirectBuffers(input: Array[Byte], legacy: Boolean): Array[Byte] = {
    val dict_size  = 32 * 1024
    val trainer = new ZstdDictTrainer(input.length, dict_size)
    trainer.addSample(input)
    val dict_buff = trainer.trainSamples(legacy)
    if (dict_buff.length > 0)
      dict_buff
    else
      Array.empty[Byte]
  }

  def trainDict(input: Array[Byte], legacy: Boolean): Array[Byte] = {
    val dict_buff = Array.fill[Byte](32*1024)(0)
    val dict_size = if (legacy) {
      Zstd.trainFromBuffer(Array(input), dict_buff, true).toInt
    } else {
      Zstd.trainFromBuffer(Array(input), dict_buff).toInt
    }
    if (dict_size > 0) {
      val dictDirectBuffer = trainDictDirectBuffers(input, legacy);
      val dictRef = dict_buff.slice(0, dict_size)
      assert(dictDirectBuffer sameElements dictRef)
      dictRef
    }
    else
      Array.empty[Byte]
  }

  val legacyS = List(true, false)
  for (level <- levels; legacy <- legacyS) {

    "Zstd" should s"should round-trip compression/decompression with dict at level $level with legacy $legacy" in {
      check { input: Array[Byte] =>
        val in = input.map { byte => (byte % 4).toByte }
        val size = input.length
        val dict = trainDict(in, legacy)
        (dict.size > 0) ==> {
          val compressed = Zstd.compressUsingDict(in, dict, level)
          val decompressed = Zstd.decompress(compressed, dict, size)
          in.toSeq == decompressed.toSeq
        }
      }
    }

    it should s"should round-trip compression/decompression ByteBuffers with dict at level $level with legacy $legacy" in {
      check { input: Array[Byte] =>
        val in = input.map { byte => (byte % 4).toByte }
        val size = input.length
        val inBuf = ByteBuffer.allocateDirect(size)
        inBuf.put(in)
        inBuf.flip()
        val dict = trainDict(in, legacy)
        (dict.size > 0) ==> {
          val compressed = ByteBuffer.allocateDirect(Zstd.compressBound(size).toInt);
          Zstd.compress(compressed, inBuf, dict, level)
          val decompressed = ByteBuffer.allocateDirect(size)
          Zstd.decompress(decompressed, compressed, dict)
          decompressed.flip()
          val out = new Array[Byte](decompressed.remaining)
          decompressed.get(out)
          in.toSeq == out.toSeq
        }
      }
    }

    it should s"should round-trip compression/decompression with fast dict at level $level with legacy $legacy" in {
      check { input: Array[Byte] =>
        val in = input.map { byte => (byte % 4).toByte }
        val size = input.length
        val dict = trainDict(in, legacy)
        (dict.size > 0) ==> {
          val cdict = new ZstdDictCompress(dict, level)
          val compressed = Zstd.compress(in, cdict)
          cdict.close
          val ddict = new ZstdDictDecompress(dict)
          val decompressed = Zstd.decompress(compressed, ddict, size)
          ddict.close
          in.toSeq == decompressed.toSeq
        }
      }
    }

    it should s"should round-trip compression/decompression ByteBuffers with fast dict at level $level with legacy $legacy" in {
      check { input: Array[Byte] =>
        val in = input.map { byte => (byte % 4).toByte }
        val size = input.length
        val inBuf = ByteBuffer.allocateDirect(size)
        inBuf.put(in)
        inBuf.flip()
        val dict = trainDict(in, legacy)
        (dict.size > 0) ==> {
          val cdict = new ZstdDictCompress(dict, level)
          val compressed = ByteBuffer.allocateDirect(Zstd.compressBound(size).toInt);
          Zstd.compress(compressed, inBuf, cdict)
          cdict.close
          val ddict = new ZstdDictDecompress(dict)
          val decompressed = ByteBuffer.allocateDirect(size)
          Zstd.decompress(decompressed, compressed, ddict)
          decompressed.flip()
          ddict.close
          val out = new Array[Byte](decompressed.remaining)
          decompressed.get(out)
          in.toSeq == out.toSeq
        }
      }
    }

    it should s"should round-trip compression/decompression with byte[]/fast dict at level $level with legacy $legacy" in {
      check { input: Array[Byte] =>
        val in = input.map { byte => (byte % 4).toByte }
        val size = input.length
        val dict = trainDict(in, legacy)
        (dict.size > 0) ==> {
          val compressed = Zstd.compressUsingDict(in, dict, level)
          val ddict = new ZstdDictDecompress(dict)
          val decompressed = Zstd.decompress(compressed, ddict, size)
          ddict.close
          in.toSeq == decompressed.toSeq
        }
      }
    }

    it should s"should round-trip compression/decompression with fast/byte[] dict at level $level with legacy $legacy" in {
      check { input: Array[Byte] =>
        val in = input.map { byte => (byte % 4).toByte }
        val size = input.length
        val dict = trainDict(in, legacy)
        (dict.size > 0) ==> {
          val cdict = new ZstdDictCompress(dict, 0, dict.size, level)
          val compressed = Zstd.compress(in, cdict)
          cdict.close
          val decompressed = Zstd.decompress(compressed, dict, size)
          in.toSeq == decompressed.toSeq
        }
      }
    }


    it should s"compress with a byte[] and decompress with a ByteBuffer using byte[] dict $level with legacy $legacy" in {
      check { input: Array[Byte] =>
        val in = input.map { byte => (byte % 4).toByte }
        val size = input.length
        val dict = trainDict(in, legacy)
        (dict.size > 0) ==> {
          val compressed = Zstd.compressUsingDict(in, dict, level)
          val compressedBuffer = ByteBuffer.allocateDirect(compressed.size)
          compressedBuffer.put(compressed)
          compressedBuffer.limit(compressedBuffer.position())
          compressedBuffer.flip()

          val decompressedBuffer = Zstd.decompress(compressedBuffer, dict, size)
          val decompressed = new Array[Byte](size)
          decompressedBuffer.get(decompressed)
          in.toSeq == decompressed.toSeq
        }
      }
    }

    it should s"compress with a byte[] and decompress with a ByteBuffer using fast dict $level with legacy $legacy" in {
      check { input: Array[Byte] =>
        val in = input.map { byte => (byte % 4).toByte }
        val size = input.length
        val dict = trainDict(in, legacy)
        (dict.size > 0) ==> {
          val cdict = new ZstdDictCompress(dict, 0, dict.size, level)
          val compressed = Zstd.compress(in, cdict)
          val compressedBuffer = ByteBuffer.allocateDirect(compressed.size)
          compressedBuffer.put(compressed)
          compressedBuffer.flip()
          cdict.close

          val ddict = new ZstdDictDecompress(dict)
          val decompressedBuffer = Zstd.decompress(compressedBuffer, ddict, size)
          val decompressed = new Array[Byte](size)
          decompressedBuffer.get(decompressed)
          ddict.close
          in.toSeq == decompressed.toSeq
        }
      }
    }

    it should s"compress with a ByteBuffer and decompress with a byte[] using fast dict $level with legacy $legacy" in {
      check { input: Array[Byte] =>
        val in = input.map { byte => (byte % 4).toByte }
        val size = input.length
        val dict = trainDict(in, legacy)
        (dict.size > 0) ==> {
          val cdict = new ZstdDictCompress(dict, 0, dict.size, level)
          val inputBuffer = ByteBuffer.allocateDirect(size)
          inputBuffer.put(in)
          inputBuffer.flip()
          val compressedBuffer = Zstd.compress(inputBuffer, cdict)
          val compressed = new Array[Byte](compressedBuffer.limit - compressedBuffer.position)
          compressedBuffer.get(compressed)
          cdict.close
          val ddict = new ZstdDictDecompress(dict)
          val decompressed = Zstd.decompress(compressed, ddict, size)
          ddict.close
          in.toSeq == decompressed.toSeq
        }
      }
    }

    it should s"compress with a ByteBuffer and decompress with a byte[] using byte[] dict $level with legacy $legacy" in {
      check { input: Array[Byte] =>
        val in = input.map { byte => (byte % 4).toByte }
        val size = input.length
        val dict = trainDict(in, legacy)
        (dict.size > 0) ==> {
          val cdict = new ZstdDictCompress(dict, 0, dict.size, level)
          val inputBuffer = ByteBuffer.allocateDirect(size)
          inputBuffer.put(in)
          inputBuffer.flip()
          val compressedBuffer = Zstd.compress(inputBuffer, dict, level)
          val compressed = new Array[Byte](compressedBuffer.limit - compressedBuffer.position)
          compressedBuffer.get(compressed)
          cdict.close
          val decompressed = Zstd.decompress(compressed, dict, size)
          in.toSeq == decompressed.toSeq
        }
      }
    }

    it should s"should round-trip streaming compression/decompression with byte[] dict with legacy $legacy " in {
      check { input: Array[Byte] =>
        val in = input.map { byte => (byte % 4).toByte }
        val size = input.length
        val dict = trainDict(in, legacy)
        (dict.size > 0) ==> {
          val size  = input.length
          val os    = new ByteArrayOutputStream(Zstd.compressBound(size.toLong).toInt)
          val zos   = new ZstdOutputStream(os, 1)
          zos.setDict(dict)
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
          zis.setDict(dict)
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

    it should s"should round-trip streaming compression/decompression with fast dict with legacy $legacy " in {
      check { input: Array[Byte] =>
        val in = input.map { byte => (byte % 4).toByte }
        val size = input.length
        val dict = trainDict(in, legacy)
        (dict.size > 0) ==> {
          val cdict = new ZstdDictCompress(dict, 0, dict.size, 1)
          val size  = input.length
          val os    = new ByteArrayOutputStream(Zstd.compressBound(size.toLong).toInt)
          val zos   = new ZstdOutputStream(os, 1)
          zos.setDict(cdict)
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
          val ddict = new ZstdDictDecompress(dict)
          val is    = new ByteArrayInputStream(compressed)
          val zis   = new ZstdInputStream(is)
          zis.setDict(ddict)
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


    it should s"should round-trip streaming ByteBuffer compression/decompression with byte[] dict with legacy $legacy" in {
      check { input: Array[Byte] =>
        val in = input.map { byte => (byte % 4).toByte }
        val size = input.length
        val dict = trainDict(in, legacy)
        (dict.size > 0) ==> {
          val size  = input.length
          val os    = ByteBuffer.allocateDirect(Zstd.compressBound(size.toLong).toInt)

          // compress
          val ib    = ByteBuffer.allocateDirect(size)
          ib.put(input)
          val osw = new ZstdDirectBufferCompressingStream(os, 1)
          osw.setDict(dict)
          ib.flip
          osw.compress(ib)
          osw.close
          os.flip

          // for debugging
          val bytes = new Array[Byte](os.limit())
          os.get(bytes)
          os.rewind()

          // now decompress
          val zis   = new ZstdDirectBufferDecompressingStream(os)
          zis.setDict(dict)
          val output= Array.fill[Byte](size)(0)
          val block = ByteBuffer.allocateDirect(128 * 1024)
          var offset = 0
          while (zis.hasRemaining) {
            block.clear()
            val read = zis.read(block)
            block.flip()
            block.get(output, offset, read)
            offset += read
          }
          zis.close
          if (input.toSeq != output.toSeq) {
            println(s"AT SIZE $size")
            println(input.toSeq + "!=" + output.toSeq)
            println("COMPRESSED: " + bytes.toSeq)
          }
          input.toSeq == output.toSeq
        }
      }
    }

    it should s"should round-trip streaming ByteBuffer compression/decompression with fast dict with legacy $legacy" in {
      check { input: Array[Byte] =>
        val in = input.map { byte => (byte % 4).toByte }
        val size = input.length
        val dict = trainDict(in, legacy)
        (dict.size > 0) ==> {
          val cdict = new ZstdDictCompress(dict, 0, dict.size, 1)
          val size  = input.length
          val os    = ByteBuffer.allocateDirect(Zstd.compressBound(size.toLong).toInt)

          // compress
          val ib    = ByteBuffer.allocateDirect(size)
          ib.put(input)
          val osw = new ZstdDirectBufferCompressingStream(os, 1)
          osw.setDict(cdict)
          ib.flip
          osw.compress(ib)
          osw.close
          os.flip

          // for debugging
          val bytes = new Array[Byte](os.limit())
          os.get(bytes)
          os.rewind()

          // now decompress
          val zis   = new ZstdDirectBufferDecompressingStream(os)
          val ddict = new ZstdDictDecompress(dict)
          zis.setDict(ddict)
          val output= Array.fill[Byte](size)(0)
          val block = ByteBuffer.allocateDirect(128 * 1024)
          var offset = 0
          while (zis.hasRemaining) {
            block.clear()
            val read = zis.read(block)
            block.flip()
            block.get(output, offset, read)
            offset += read
          }
          zis.close
          if (input.toSeq != output.toSeq) {
            println(s"AT SIZE $size")
            println(input.toSeq + "!=" + output.toSeq)
            println("COMPRESSED: " + bytes.toSeq)
          }
          input.toSeq == output.toSeq
        }
      }
    }

  }
}
