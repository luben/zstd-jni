package com.github.luben.zstd

import org.scalatest.FlatSpec
class ZstdPerfSpec extends FlatSpec  {

  def bench(name: String, buff: Array[Byte], level: Int = 1) {
    var nsc = 0.0
    var nsd = 0.0
    var ratio = 0.0
    for (i <- 1 to 1000) {
      val start_c     = System.nanoTime
      val compressed  = Zstd.compress(buff, level)
      nsc += System.nanoTime - start_c
      val start_d     = System.nanoTime
      val size        = Zstd.decompress(buff, compressed)
      nsd += System.nanoTime - start_d
      ratio = buff.size.toDouble/compressed.size
    }
    val seconds_c = nsc / (1000 * 1000 * 1000)
    val seconds_d = nsd / (1000 * 1000 * 1000)
    val speed_c   = (buff.size / 1024.0) / seconds_c
    val speed_d   = (buff.size / 1024.0) / seconds_d
    println(s"""
      $name
      --
      Compression:        ${speed_c.toLong} MB/s
      Decompression:      ${speed_d.toLong} MB/s
      Compression Ratio:  $ratio
    """)
  }

  "Zstd" should "be fast for random data" in {
    val buff = Array.fill[Byte](1024*1024)(0)
    val rand = new scala.util.Random().nextBytes(buff)
    bench("Uncompressable data", buff)
  }

  it should "be fast for highly compressable data" in {
    val buff  = Array.fill[Byte](1024*1024)(0)
    val block = Array.fill[Byte](1024)(0)
    new scala.util.Random().nextBytes(block)
    for (i <- 0 to 1023) {
      Array.copy(block, 0, buff , i * 1024, 1024)
    }
    bench("Highly compressable data", buff)
  }

  for (level <- 1 to 9) {
    it should s"be fast for compressable data -$level" in {
      import scala.io._
      val buff = Source.fromFile("src/test/resources/src.tar")(Codec.ISO8859).map{_.toByte }.take(1024*1024).toArray
      bench(s"Compressable data -$level", buff, level)
    }
  }

}
