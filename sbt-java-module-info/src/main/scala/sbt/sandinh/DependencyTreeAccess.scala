package sbt
package sandinh

import sbt.internal.graph.ModuleGraph
import sbt.plugins.DependencyTreeKeys.dependencyTreeModuleGraph0
import sbt.plugins.DependencyTreeSettings.baseBasicReportingSettings
import sbt.plugins.MiniDependencyTreeKeys.dependencyTreeIncludeScalaLibrary

object DependencyTreeAccess {
  val moduleInfoDepGraph = taskKey[ModuleGraph]("The dependency graph for a project")
  val settings = inConfig(Runtime)(baseBasicReportingSettings) ++ Seq(
    dependencyTreeIncludeScalaLibrary := true,
    Compile / moduleInfoDepGraph := (Compile / dependencyTreeModuleGraph0).value,
    Runtime / moduleInfoDepGraph := (Runtime / dependencyTreeModuleGraph0).value,
  )
  def toDepsMap(g: ModuleGraph): Map[String, Set[String]] = g.edges
    .map { case (from, to) =>
      s"${from.organization}:${from.name}" -> s"${to.organization}:${to.name}"
    }
    .groupBy(_._1)
    .mapValues(_.map(_._2).toSet)
}
