package com.github.luben.zstd

import org.scalatest.FlatSpec

import scala.io._
import java.io._
import java.nio.ByteBuffer

class ZstdPerfSpec extends FlatSpec  {

  def report(name: String, compressed: Int, size: Int, cycles: Int, nsc: Double, nsd: Double ): Unit = {
    val ns = 1000L * 1000 * 1000
    val mb = 1024 * 1024

    val ratio = size.toDouble / compressed
    val seconds_c = nsc.toDouble / ns
    val seconds_d = nsd.toDouble / ns
    val total_mb  = cycles.toDouble * size / mb
    val speed_c   = total_mb / seconds_c
    val speed_d   = total_mb / seconds_d

    println(s"""
      $name
      --
      Compression:        ${speed_c.toLong} MB/s
      Decompression:      ${speed_d.toLong} MB/s
      Compression Ratio:  $ratio
    """)

  }

  def bench(name: String, input: Array[Byte], level: Int = 1): Unit = {
    var nsc = 0.0
    var nsd = 0.0
    var ratio = 0.0
    val cycles = 200
    val output: Array[Byte] = Array.fill[Byte](input.size)(0)
    var compressedSize = 0
    for (i <- 1 to cycles) {
      val start_c     = System.nanoTime
      val compressed  = Zstd.compress(input, level)
      nsc += System.nanoTime - start_c
      compressedSize  = compressed.size
      val start_d     = System.nanoTime
      val size        = Zstd.decompress(output, compressed)
      nsd += System.nanoTime - start_d
    }
    report(name, compressedSize, input.size, cycles, nsc, nsd)
    assert (input.toSeq == output.toSeq)
  }

  def benchDirectByteBuffer(name: String, input: Array[Byte], level: Int = 1): Unit = {
    var nsc = 0.0
    var nsd = 0.0
    var ratio = 0.0
    val cycles = 200
    val outputBuffer = ByteBuffer.allocateDirect(input.size)
    val inputBuffer = ByteBuffer.allocateDirect(input.size)
    inputBuffer.put(input)
    var compressedSize = 0
    for (i <- 1 to cycles) {
      val start_c     = System.nanoTime
      inputBuffer.rewind()
      val compressedBuffer  = Zstd.compress(inputBuffer, level)
      nsc += System.nanoTime - start_c
      compressedSize  = compressedBuffer.limit()
      val start_d     = System.nanoTime
      outputBuffer.clear()
      val size        = Zstd.decompress(outputBuffer, compressedBuffer)
      nsd += System.nanoTime - start_d
    }
    report(name, compressedSize, input.size, cycles, nsc, nsd)
    assert (inputBuffer.compareTo(outputBuffer) == 0)
  }

  def benchStream(name: String, input: Array[Byte], level: Int = 1): Unit = {
    val cycles = 100
    val size  = input.length
    var compressed: Array[Byte] = null

    val c_start = System.nanoTime
    for (i <- 1 to cycles)  {
      val os = new ByteArrayOutputStream(Zstd.compressBound(size.toLong).toInt)
      val zos = new ZstdOutputStream(os, level)
      zos.write(input)
      zos.close
      if (compressed == null)
        compressed = os.toByteArray
    }
    val nsc = System.nanoTime - c_start

    val output= Array.fill[Byte](size)(0)
    val d_start = System.nanoTime
    for (i <- 1 to cycles)  {

      // now decompress
      val is    = new ByteArrayInputStream(compressed)
      val zis   = new ZstdInputStream(is)
      var ptr   = 0

      while (ptr < size) {
        ptr += zis.read(output, ptr, size - ptr)
      }
      zis.close

    }
    val nsd = System.nanoTime - d_start

    report(name, compressed.size, size, cycles, nsc, nsd)
    assert(input.toSeq == output.toSeq)
  }

  def benchDirectBufferStream(name: String, input: Array[Byte], level: Int = 1): Unit = {
    val cycles = 100

    val compressedBuffer = ByteBuffer.allocateDirect(input.size)
    val inputBuffer = ByteBuffer.allocateDirect(input.size)
    inputBuffer.put(input)
    inputBuffer.flip();
    val c_start = System.nanoTime
    Zstd.compress(compressedBuffer, inputBuffer, level);
    val nsc = (System.nanoTime - c_start) * cycles
    compressedBuffer.flip();

    val decompressed = ByteBuffer.allocateDirect(input.size);
    val d_start = System.nanoTime
    for (i <- 1 to cycles)  {
      compressedBuffer.rewind()
      decompressed.clear();
      val zstr = new ZstdDirectBufferDecompressingStream(compressedBuffer)
      while (decompressed.hasRemaining) {
        zstr.read(decompressed)
      }
      zstr.close()
    }
    val nsd = System.nanoTime - d_start

    val output = new Array[Byte](input.size)
    decompressed.flip()
    decompressed.get(output)

    report(name, compressedBuffer.limit(), input.size, cycles, nsc, nsd)
    assert(input.toSeq == output.toSeq)
  }

  val buff = Source.fromFile("src/test/resources/xml")(Codec.ISO8859).map{_.toByte }.take(1024 * 1024).toArray
  for (level <- List(1, 3, 6, 9)) {
    it should s"be fast for compressable data -$level" in {
      import scala.io._
      bench(s"Compressable data at $level", buff, level)
      benchDirectByteBuffer(s"Compressable data at $level in a direct ByteBuffer", buff, level)
    }
  }

  val buff1 = Source.fromFile("src/test/resources/xml")(Codec.ISO8859).map{_.toByte }.take(5 * 1024 * 1024).toArray
  for (level <- List(1, 3, 6, 9)) {
    it should s"be fast with steaming -$level" in {
        benchStream(s"Streaming at $level", buff1, level)
        benchDirectBufferStream(s"Streaming at $level to direct ByteBuffers", buff1, level)
    }
  }

}
