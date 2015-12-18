package com.github.luben.zstd

import org.scalatest.FlatSpec
import org.scalatest.prop.Checkers
import org.scalacheck.Arbitrary._
import org.scalacheck.Prop._
import java.io._
import scala.io._

class ZstdSpec extends FlatSpec with Checkers {
  implicit override val generatorDrivenConfig =
    PropertyCheckConfig(minSize = 0, maxSize = 256 * 1024)

  for (level <- List(1,3,6,9)) {
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

  for (level <- List(1,3,6,9)) {
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

  "ZstdInputStream" should "be able to consume files compressed by the zstd binary" in {
    val orig = new File("src/test/resources/xml")
    val file = new File("src/test/resources/xml.zst")
    val fis  = new FileInputStream(file)
    val zis  = new ZstdInputStream(fis)
    val buff = Array.fill[Byte](orig.length.toInt)(0)
    zis.read(buff, 0, orig.length.toInt)
    val original = Source.fromFile(orig)(Codec.ISO8859)
    original.toSeq == buff.toSeq
  }

  "ZstdOutputStream" should "produce the same compressed file as zstd binary" in {
    val file = new File("src/test/resources/xml")
    val fis  = new FileInputStream(file)
    val buff = Array.fill[Byte](file.length.toInt)(0)
    fis.read(buff, 0, file.length.toInt)

    val os  = new ByteArrayOutputStream(Zstd.compressBound(file.length).toInt)
    val zos = new ZstdOutputStream(os, 9)
    zos.write(buff)
    zos.close

    val compressed = os.toByteArray
    val zst = Source.fromFile("src/test/resources/xml.zst")(Codec.ISO8859)

    zst.toSeq == compressed.toSeq
  }

}
