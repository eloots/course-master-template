/**
  * Copyright © 2014, 2015 Typesafe, Inc. All rights reserved. [http://www.typesafe.com]
  */
package sbtstudent

import sbt._
import Keys._
import scala.Console
import StudentKeys._

object Navigation {
  def mapExercisesFromProjects(state: State, reverse: Boolean = false): Map[String, String] = {
    val refs = Project.extract(state).structure.allProjectRefs.toList.map(r => r.project).filter(_.startsWith("exercise_")).sorted
    val refsReordered = if (reverse) refs.reverse else refs
    val mapping = (refsReordered zip refsReordered.tail) :+ (refsReordered.last, refsReordered.last)
    mapping.toMap
  }

  def mapExercisesFromFolders(state: State, reverse: Boolean = false): Map[String, String] = {
    object FoldersOnly {
      def apply() = new FoldersOnly
    }
    class FoldersOnly extends java.io.FileFilter {
      override def accept(f: File): Boolean = f.isDirectory
    }

    val solF = new sbt.File(new sbt.File(Project.extract(state).structure.root), ".cue")
    val ExerciseNameSpec = """.*exercise_[0-9][0-9][0-9]_\w+$""".r

    def isExerciseFolder(folder: File): Boolean = {
      ExerciseNameSpec.findFirstIn(folder.getPath).isDefined
    }
    val refs = IO.listFiles(solF, FoldersOnly()).filter(isExerciseFolder).map(_.getName).toList.sorted
    val refsReordered = if (reverse) refs.reverse else refs
    val mapping = (refsReordered zip refsReordered.tail) :+ (refsReordered.last, refsReordered.last)
    mapping.toMap
  }

  def cueFolderExists(state: State): Boolean = {
    val cueFolder = new sbt.File(new sbt.File(Project.extract(state).structure.root), ".cue")
    cueFolder.exists()
  }

  def mapExercises(state: State, reverse: Boolean = false): Map[String, String] = {
    if (cueFolderExists(state)) {
      mapExercisesFromFolders(state, reverse)
    } else {
      mapExercisesFromProjects(state, reverse)
    }
  }


  val setupNavAttrs: (State) => State = (s: State) => {
    val mark: File = s get bookmark getOrElse new sbt.File(new sbt.File(Project.extract(s).structure.root), ".bookmark")
    val prev: Map[String, String] = s get mapPrev getOrElse mapExercises(s, reverse = true)
    val next: Map[String, String] = s get mapNext getOrElse mapExercises(s)
    s.put(bookmark, mark).put(mapPrev, prev).put(mapNext, next)
  }

  val loadBookmark: (State) => State = (state: State) => {
    if (cueFolderExists(state)) {
      state
    } else {
      val key: AttributeKey[File] = AttributeKey[File](bookmarkKeyName)
      val bookmarkFile: Option[File] = state get key
      try {
        val mark: String = IO.read(bookmarkFile.get)
        val cmd: String = s"project $mark"
        val newState = Command.process(cmd, state)
        newState
      } catch {
        case e: java.io.FileNotFoundException =>
          val mark = mapExercises(state).toList.map(_._1).sorted.headOption
          if (mark.isDefined) {
            val cmd: String = s"project ${mark.get}"
            Command.process(cmd, state)
          } else {
            println(s"ERROR: No exercises found in repo")
            state
          }
      }
    }
  }

  def nextExercise: Command = Command.command("nextExercise") { state =>
    move(mapNextKeyName, state, "next")
  }

  def prevExercise: Command = Command.command("prevExercise") { state =>
    move(mapPrevKeyName, state, "prev")
  }

  def getExerciseName(state: State): String = {
    if (cueFolderExists(state)) {
      val key: AttributeKey[File] = AttributeKey[File](bookmarkKeyName)
      val bookmarkFile: Option[File] = state get key
      val mark: String = IO.readLines(bookmarkFile.get).head
      mark
    } else {
      Project.extract(state).get(name)
    }
  }

  def move(keyName: String, state: State, direction: String): State = {
    val attrKey = AttributeKey[Map[String, String]](keyName)
    val prjNme: String = getExerciseName(state)
    val moveMap: Option[Map[String, String]] = state get attrKey
    val toPrjNme: String = moveMap.get.getOrElse(prjNme, moveMap.get.head._1)
    (cueFolderExists(state), toPrjNme, direction) match {
      case (_, `prjNme`, "prev") =>
        Console.println(Console.BLUE + "[WARNING] " + Console.RESET + "You're already at the first exercise")
        state
      case (_, `prjNme`, "next") =>
        Console.println(Console.BLUE + "[WARNING] " + Console.RESET + "You're already at the last exercise")
        state
      case (false, _, _) =>
        var cmd: String = s"project $toPrjNme"
        val newState: State = Command.process(cmd, state)
        writeBookmark(toPrjNme, newState)
        newState
      case (true, toProjectName, _) =>
        val src = Project.extract(state).get(sourceDirectory)
        // update tests (can be previous or next...)
        val newExerciseSrc = new sbt.File(new sbt.File(Project.extract(state).structure.root), s".cue/$toProjectName/src")
        for {
          f <- List("test", "multi-jvm")
          fromFolder = new File(newExerciseSrc, f)
          toFolder = new File(src, f)
        } {
          IO.delete(toFolder)
          IO.copyDirectory(fromFolder, toFolder)
        }
        writeBookmark(toProjectName, state)
        Console.println(s"""${Console.GREEN}[INFO]${Console.RESET} Moved to ${Console.BLUE}${toProjectName}${Console.RESET}""")
        state
    }
  }

  def writeBookmark(toPrjNme: String, state: State): Unit = {
    val key: AttributeKey[File] = AttributeKey[File](bookmarkKeyName)
    val bookmarkFile: Option[File] = state get key
    IO.write(bookmarkFile.get, toPrjNme)
  }
}
