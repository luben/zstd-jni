package com.joprice

object Basic {
  System.loadLibrary("basic")

  def main(args: Array[String]): Unit = {
    val basic = new Basic()
    val result = basic.compute(2, 3)
    println(result)
  }
}

class Basic {
  @native def compute(a: Int, b: Int): Int
}

