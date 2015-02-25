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
        val size = input.length
        val is = new ByteArrayInputStream(input)
        val os = new ByteArrayOutputStream(size)
        val buff = Array.fill[Byte](128*1024)(0)
        var read = 0
        do {
          read = is.read(buff, 0, buff.size)
          if (read > 0)
            os.write(buff, 0, read)
        } while (read > 0)
        os.close
        is.close
        input.toSeq == os.toByteArray.seq
      }
    }
  }
}
