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

  val levels = List(1,3,6,9,16)

  def trainDictDirectBuffersLegacy(input: Array[Byte]) = {
    val dict_size  = 32 * 1024
    val trainer = new ZstdDictTrainer(input.length, dict_size)
    trainer.addSample(input)
    val dict_buff = trainer.trainSamples(true)
    if (dict_buff.length > 0)
      dict_buff
    else
      Array.empty[Byte]
  }

  def trainDictDirectBuffers(input: Array[Byte]) = {
    val dict_size  = 32 * 1024
    val trainer = new ZstdDictTrainer(input.length, dict_size)
    trainer.addSample(input)
    val dict_buff = trainer.trainSamples()
    if (dict_buff.length > 0)
      dict_buff
    else
      Array.empty[Byte]
  }

  def trainDict(input: Array[Byte]) = {
    val dict_buff = Array.fill[Byte](32*1024)(0)
    val dict_size = Zstd.trainFromBuffer(Array(input), dict_buff).toInt
    if (dict_size > 0) {
      val dictDirectBuffer = trainDictDirectBuffers(input);
      val dictRef = dict_buff.slice(0, dict_size)
      assert(dictDirectBuffer sameElements dictRef)
      dictRef
    }
    else
      Array.empty[Byte]
  }

  def trainDictLegacy(input: Array[Byte]) = {
    val dict_buff = Array.fill[Byte](32*1024)(0)
    val dict_size = Zstd.trainFromBuffer(Array(input), dict_buff, true).toInt
    if (dict_size > 0) {
      val dictDirectBuffer = trainDictDirectBuffers(input);
      val dictRef = dict_buff.slice(0, dict_size)
      assert(dictDirectBuffer sameElements dictRef)
      dictRef
    }
    else
      Array.empty[Byte]
  }

  for (level <- levels) {
    "Zstd" should s"should round-trip compression/decompression with dict; legacy trainer; at level $level" in {
      check { input: Array[Byte] =>
        val in          = input.map{ byte => (byte % 4).toByte }
        val size        = input.length
        val dict        = trainDictLegacy(in)
        (dict.size > 0) ==> {
          val compressed  = Zstd.compressUsingDict(in, dict, level)
          val decompressed= Zstd.decompress(compressed, dict, size)
          in.toSeq == decompressed.toSeq
        }
      }
    }

    "Zstd" should s"should round-trip compression/decompression with dict; direct buffers; legacy trainer; at level $level" in {
      check { input: Array[Byte] =>
        val in          = input.map{ byte => (byte % 4).toByte }
        val size        = input.length
        val dict        = trainDictDirectBuffersLegacy(in)
        (dict.size > 0) ==> {
          val compressed  = Zstd.compressUsingDict(in, dict, level)
          val decompressed= Zstd.decompress(compressed, dict, size)
          in.toSeq == decompressed.toSeq
        }
      }
    }

    "Zstd" should s"should round-trip compression/decompression with dict at level $level" in {
      check { input: Array[Byte] =>
        val in          = input.map{ byte => (byte % 4).toByte }
        val size        = input.length
        val dict        = trainDict(in)
        (dict.size > 0) ==> {
          val compressed  = Zstd.compressUsingDict(in, dict, level)
          val decompressed= Zstd.decompress(compressed, dict, size)
          in.toSeq == decompressed.toSeq
        }
      }
    }

    it should s"should round-trip compression/decompression with fast dict at level $level" in {
      check { input: Array[Byte] =>
        val in          = input.map{ byte => (byte % 4).toByte }
        val size        = input.length
        val dict        = trainDict(in)
        (dict.size > 0) ==> {
          val cdict       = new ZstdDictCompress(dict, 0, dict.size, level)
          val compressed  = Zstd.compress(in, cdict)
          cdict.close
          val ddict       = new ZstdDictDecompress(dict)
          val decompressed= Zstd.decompress(compressed, ddict, size)
          ddict.close
          in.toSeq == decompressed.toSeq
        }
      }
    }

    it should s"should round-trip compression/decompression with byte[]/fast dict at level $level" in {
      check { input: Array[Byte] =>
        val in          = input.map{ byte => (byte % 4).toByte }
        val size        = input.length
        val dict        = trainDict(in)
        (dict.size > 0) ==> {
          val compressed  = Zstd.compressUsingDict(in, dict, level)
          val ddict       = new ZstdDictDecompress(dict)
          val decompressed= Zstd.decompress(compressed, ddict, size)
          ddict.close
          in.toSeq == decompressed.toSeq
        }
      }
    }

    it should s"should round-trip compression/decompression with fast/byte[] dict at level $level" in {
      check { input: Array[Byte] =>
        val in          = input.map{ byte => (byte % 4).toByte }
        val size        = input.length
        val dict        = trainDict(in)
        (dict.size > 0) ==> {
          val cdict       = new ZstdDictCompress(dict, 0, dict.size, level)
          val compressed  = Zstd.compress(in, cdict)
          cdict.close
          val decompressed= Zstd.decompress(compressed, dict, size)
          in.toSeq == decompressed.toSeq
        }
      }
    }


    it should s"compress with a byte[] and decompress with a ByteBuffer using byte[] dict $level" in {
      check { input: Array[Byte] =>
        val in          = input.map{ byte => (byte % 4).toByte }
        val size        = input.length
        val dict        = trainDict(in)
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

    it should s"compress with a byte[] and decompress with a ByteBuffer using fast dict $level" in {
      check { input: Array[Byte] =>
        val in          = input.map{ byte => (byte % 4).toByte }
        val size        = input.length
        val dict        = trainDict(in)
        (dict.size > 0) ==> {
          val cdict      = new ZstdDictCompress(dict, 0, dict.size, level)
          val compressed = Zstd.compress(in, cdict)
          val compressedBuffer = ByteBuffer.allocateDirect(compressed.size)
          compressedBuffer.put(compressed)
          compressedBuffer.limit(compressedBuffer.position())
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

    it should s"compress with a ByteBuffer and decompress with a byte[] using fast dict $level" in {
      check { input: Array[Byte] =>
        val in          = input.map{ byte => (byte % 4).toByte }
        val size        = input.length
        val dict        = trainDict(in)
        (dict.size > 0) ==> {
          val cdict       = new ZstdDictCompress(dict, 0, dict.size, level)
          val inputBuffer = ByteBuffer.allocateDirect(size)
          inputBuffer.put(in)
          inputBuffer.limit(inputBuffer.position)
          inputBuffer.flip()
          val compressedBuffer = Zstd.compress(inputBuffer, cdict)
          val compressed = new Array[Byte](compressedBuffer.limit - compressedBuffer.position)
          compressedBuffer.get(compressed)
          cdict.close
          val ddict       = new ZstdDictDecompress(dict)
          val decompressed = Zstd.decompress(compressed, ddict, size)
          ddict.close
          in.toSeq == decompressed.toSeq
        }
      }
    }

    it should s"compress with a ByteBuffer and decompress with a byte[] using byte[] dict $level" in {
      check { input: Array[Byte] =>
        val in          = input.map{ byte => (byte % 4).toByte }
        val size        = input.length
        val dict        = trainDict(in)
        (dict.size > 0) ==> {
          val cdict       = new ZstdDictCompress(dict, 0, dict.size, level)
          val inputBuffer = ByteBuffer.allocateDirect(size)
          inputBuffer.put(in)
          inputBuffer.limit(inputBuffer.position)
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
  }
}
