package com.sandinh.javamodule.moduleinfo

import com.sandinh.javamodule.moduleinfo.Utils.toSlash
import org.jetbrains.annotations.{NotNull, Nullable}
import org.objectweb.asm.{ClassWriter, Opcodes}

sealed trait ModuleSpec {

  /** Id of this module in format `organization:name`
    * Note `name` contains the scala version suffix like `_2.13` if this module is a scala depended lib
    * For `moduleInfo` task, this field will be automatically generated
    * For other cases, this must not be null
    */
  @Nullable def id: String

  /** The Module Name of the Module to construct */
  def moduleName: String

  /** identifiers like List(org1:name1, org2:name2) of dependencies will be merged to the .jar file of this module.
    * The Java Module System does not allow the same package to be used in more than one module.
    * This is an issue with legacy libraries, where it was common practice to use the same package in multiple Jars.
    * This plugin offers the option to merge multiple Jars into one in such situations.
    * Note: ignore for `moduleInfo` task
    */
  def mergedJars: List[String]

  def withDefaultId(@NotNull id: String): ModuleSpec
}

final case class KnownModule(
    /** @inheritdoc */
    moduleName: String,
    /** @inheritdoc */
    @Nullable id: String = null,
) extends ModuleSpec {
  def mergedJars: List[String] = Nil
  def withDefaultId(@NotNull id: String): KnownModule = if (this.id != null) this else copy(id = id)
}

final case class AutomaticModule(
    /** @inheritdoc */
    moduleName: String,
    /** @inheritdoc */
    @Nullable id: String = null,
    /** @inheritdoc */
    mergedJars: List[String] = Nil,
) extends ModuleSpec {
  def withDefaultId(@NotNull id: String): AutomaticModule = if (this.id != null) this else copy(id = id)
}

final case class JpmsModule(
    /** @inheritdoc */
    moduleName: String,
    /** @inheritdoc */
    @Nullable id: String = null,
    @Nullable moduleVersion: String = null,
    openModule: Boolean = true,
    exports: Set[String] = Set.empty,
    opens: Set[String] = Set.empty,
    /** Ex: requires = Set("org.apache.commons.logging" -> Require.Transitive) */
    requires: Set[(String, Require)] = Set.empty,
    uses: Set[String] = Set.empty,
    providers: Map[String, List[String]] = Map.empty,
    mainClass: Option[String] = None,
    /** @inheritdoc */
    mergedJars: List[String] = Nil,
    /** allows you to ignore some unwanted services from being automatically converted into
      * provides .. with ... declarations
      * Note: ignore for `moduleInfo` task
      */
    ignoreServiceProviders: Set[String] = Set.empty,
    /** Set = true to add `requires (transitive|static)` directives based on dependencies of the project
      * Note: Default true if `requires.isEmpty`
      */
    @Nullable private val requireAllDefinedDependencies: java.lang.Boolean = null,
    /** Set = true to add an `exports` for each package found in the Jar
      * Note: Default = true if `exports.isEmpty`
      */
    @Nullable private val exportAllPackages: java.lang.Boolean = null,
) extends ModuleSpec {
  def requireAll: Boolean =
    if (requireAllDefinedDependencies != null) requireAllDefinedDependencies
    else requires.isEmpty

  def exportAll: Boolean =
    if (exportAllPackages != null) exportAllPackages
    else exports.isEmpty

  def toModuleInfoClass: Array[Byte] = {
    val cw = new ClassWriter(0)
    cw.visit(Opcodes.V9, Opcodes.ACC_MODULE, "module-info", null, null, null)
    val moduleVisitor = cw.visitModule(
      moduleName,
      if (openModule) Opcodes.ACC_OPEN else 0,
      moduleVersion
    )
    mainClass.foreach(moduleVisitor.visitMainClass)
    exports.map(toSlash).foreach(moduleVisitor.visitExport(_, 0))
    opens.map(toSlash).foreach(moduleVisitor.visitOpen(_, 0))
    moduleVisitor.visitRequire("java.base", 0, null)
    requires.foreach { case (module, access) => moduleVisitor.visitRequire(module, access.code, null) }
    uses.map(toSlash).foreach(moduleVisitor.visitUse)
    providers.foreach { case (name, implementations) =>
      moduleVisitor.visitProvide(
        toSlash(name),
        implementations.map(toSlash)*
      )
    }
    moduleVisitor.visitEnd()
    cw.visitEnd()
    cw.toByteArray
  }

  def withDefaultId(@NotNull id: String): JpmsModule = if (this.id != null) this else copy(id = id)
}

sealed trait Require {
  final def code: Int = this match {
    case Require.Default    => 0
    case Require.Transitive => Opcodes.ACC_TRANSITIVE
    case Require.Static     => Opcodes.ACC_STATIC_PHASE
  }
}
object Require {
  case object Default extends Require
  case object Transitive extends Require
  case object Static extends Require
}
