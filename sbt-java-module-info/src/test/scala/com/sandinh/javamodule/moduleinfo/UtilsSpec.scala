package com.sandinh.javamodule.moduleinfo
import org.scalatest.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must
import java.nio.file.Files
import java.util.zip.ZipEntry
import com.sandinh.javamodule.moduleinfo.Utils.*

class UtilsSpec extends AnyFlatSpec with must.Matchers {
  "Utils.JarFileOps" should "addAutoModuleName" in {
    val path = Files.createTempDirectory("addAutoModuleName")
    val f = path.resolve("addAutoModuleName.jar").toFile
    f.jarOutputStream() { jos =>
      val e = new ZipEntry("foo.txt")
      jos.putNextEntry(e)
      jos.write("hello".getBytes("UTF-8"))
      jos.closeEntry()
    }
    f.isFile mustBe true
    f.jarInputStream(_.lazyList.headOption) mustBe defined

    f.addAutomaticModuleName("foo.bar")

    val man = f.jarInputStream(_.getManifest)
    man must not be (null)
    man.getMainAttributes.getValue("Automatic-Module-Name") mustBe "foo.bar"
    val e = f.jarInputStream(_.lazyList.headOption)
    e mustBe defined
    e.get.getName mustBe "foo.txt"
  }
}
