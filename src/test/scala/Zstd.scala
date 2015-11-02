package com.github.luben.zstd

import org.scalatest.FlatSpec
import org.scalatest.prop.Checkers
import org.scalacheck.Arbitrary._
import org.scalacheck.Prop._
import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

class ZstdSpec extends FlatSpec with Checkers {
  implicit override val generatorDrivenConfig =
    PropertyCheckConfig(minSize = 0, maxSize = 1024*1024)

  "Zstd" should "should round-trip compression/decompression" in {
    check { input: Array[Byte] =>
      {
        val size        = input.length
        val compressed  = Zstd.compress(input)
        val decompressed= Zstd.decompress(compressed, size)
        input.toSeq == decompressed.toSeq
      }
    }
  }

 it should "should round-trip using streaming API" in {
    check { input: Array[Byte] =>
      {
        val size  = input.length
        val os    = new ByteArrayOutputStream(Zstd.compressBound(size.toLong).toInt)
        val zos   = new ZstdOutputStream(os)
        var ptr   = 0
        while (ptr < size - 1) {
          val chunk = if (size - ptr > 128 * 1024) 128 * 1024 else size - ptr
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

        while (ptr < size - 1) {
          val chunk = if (size - ptr > 128 * 1024) 128 * 1024 else size - ptr
          zis.read(output, ptr, chunk)
          ptr += chunk
        }
        zis.close
        input.toSeq == output.toSeq
      }
    }
  }
}
