# sbt java module info
A sbt plugin to generate module-info.class for your projects and use legacy libraries as Java Modules

## How to use
1. Add to project/plugins.sbt
```sbt
addSbtPlugin("com.sandinh" % "sbt-java-module-info" % sbt-java-module-info-version)
```
2. Set `moduleInfos` (should be in `Global` scope)
```sbt
// With this, the legacy .jar files of your libraryDependencies will be transformed to modules
// which are stored in target/moduleInfo directory
Global / moduleInfos := Seq(
  // Add `Automatic-Module-Name: paranamer` to paranamer.jar/META-INF/MANIFEST.MF
  AutomaticModule(
    "com.thoughtworks.paranamer:paranamer", // id in format org:name
    "paranamer" // module name
  ),
  AutomaticModule("org.slf4j:slf4j-api", "org.slf4j",
    /** identifiers like List(org1:name1, org2:name2) of dependencies will be merged to the .jar file of this module.
     * The Java Module System does not allow the same package to be used in more than one module.
     * This is an issue with legacy libraries, where it was common practice to use the same package in multiple Jars.
     * This plugin offers the option to merge multiple Jars into one in such situations.
     * Note: ignore for `moduleInfo` task
     */
    mergeJars = List("org.slf4j:slf4j-ext")
  ),
  // Generate and add module-info.class to jackson-module-scala_3.jar to transform it to a module
  JpmsModule(
    "com.fasterxml.jackson.module:jackson-module-scala_3",
    "com.fasterxml.jackson.scala",
    // other fields as above:
    // exports, opens, uses, providers, requireAllDefinedDependencies, exportAllPackages, mergeJars

    /** allows you to ignore some unwanted services from being automatically converted into
     * provides .. with ... declarations
     */
    ignoreServiceProviders = Set("org.some.provider")
  ),
)
```
3. Enable the plugin and add settings to your sbt projects
```sbt
lazy val foo = project.enablePlugins(ModuleInfoPlugin).settings(
  // Use this to generate module-info.class
  moduleInfo := JpmsModule(
    "com.my.foo.module.name", // foo's moduleName
    openModule = false, // If you don't want to open module com.my.foo.module.name
    exports = Set("com.package1", "org.foo.bar"),
    opens = Set("com.package1"),
    requires = Set("org.apache.commons.logging" -> Require.Transitive),
    // similar for uses, providers

    // if you want to auto generate the `requires` field above based on project's dependencies
    // then you set this = true (default = true if `requires.isEmpty`)
    requireAllDefinedDependencies = true,
    // if you want to auto generate the `exports` field above based on the classes in your project
    // then you set this = true (default = true if `exports.isEmpty`)
    exportAllPackages = true,
  ),
).dependsOn(baz)
lazy val baz = project.enablePlugins(ModuleInfoPlugin).settings(
  // Use this to set Automatic-Module-Name field in MANIFEST.MF
  moduleInfo := AutomaticModule("com.my.baz.module.name")
)
```
4. Done
This plugin will update the following sbt tasks:
+ `products` --> will also create module-info.class for your project if moduleInfo is a JpmsModule
  (so `Compile/packageBin` will package it into your jar)
+ `Compile / packageBin / packageOptions` --> will add `ManifestAttributes("Automatic-Module-Name" -> moduleName)`
+ `update` --> will transform the legacy .jar files of your libraryDependencies
  (So other tasks will be updated as well, like `Runtime/managedClasspath`, `Compile/fullClasspath`,..)

## License
Apache License 2.0

### Similar works
https://github.com/moditect/moditect
https://github.com/gradlex-org/extra-java-module-info
