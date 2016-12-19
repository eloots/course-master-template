package sbtstudent

import sbt._
import Keys._
import Navigation.{ loadBookmark, setupNavAttrs }
import scala.Console
import scala.util.matching._

object StudentCommandPlugin extends AutoPlugin {
  override val requires = sbt.plugins.JvmPlugin
  override val trigger: PluginTrigger = allRequirements
  object autoImport {
  }
  override lazy val globalSettings =
    Seq(
      commands in Global ++= Seq(Man.man, Navigation.nextExercise, Navigation.prevExercise),
      onLoad in Global := {
        val state = (onLoad in Global).value
        loadBookmark compose(setupNavAttrs compose state)
      }
    ) ++
      AdditionalSettings.initialCmdsConsole ++
      AdditionalSettings.initialCmdsTestConsole ++
      AdditionalSettings.cmdAliases

  override lazy val projectSettings =
    Seq(
      shellPrompt := { state =>
        val base: File = Project.extract(state).get(sourceDirectory)
        val basePath: String = base + "/test/resources/README.md"
        val exercise = Console.BLUE + IO.readLines(new sbt.File(basePath)).head + Console.RESET
        val manRmnd = Console.RED + "man [e]" + Console.RESET
        val prjNbrNme = IO.readLines(new sbt.File(new sbt.File(Project.extract(state).structure.root), ".courseName")).head
        s"$manRmnd > $prjNbrNme > $exercise > "
      }
    )
}
