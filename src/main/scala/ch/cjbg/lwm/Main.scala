package ch.cjbg.lwm

import better.files._

import java.time.LocalDateTime
import scala.language.existentials

import File._
import scala.util.Try
import scala.util.Success
import scala.util.Failure

object Main extends App {
  args.toList match {
    case writersFileName :: imagesFileName :: separator :: outputDirName :: Nil => {
      log(
        s"writersFileName: '$writersFileName', imagesFileName: '$imagesFileName', separator: '$separator', outputDirName: '$outputDirName'"
      )

      log(s"Reading '$writersFileName' and '$imagesFileName' CSV files")
      val writersFile = File(writersFileName)
      val imagesFile = File(imagesFileName)

      log("Decoding CSV files")
      val writers = processWriters(writersFile, separator)
      val images = processImages(imagesFile, separator)

      log("Check fullnames existence / matching in both files")
      val tryCheckImagesExist = checkImagesFullnamesExistence(images, writers)
      returnSuccessPrintFailures(tryCheckImagesExist)

      log("Check images paths existence")
      val tryCheckImagesPath = checkImagesPathsExistence(images)
      returnSuccessPrintFailures(tryCheckImagesPath)

      log("Make directories, copy images sources and write XML file")
      mkdirsAndCopyAndXmlWriting(writers, images, outputDirName)
    }
    case _ => {
      println(
        "Give args: <writersFileName> <imagesFileName> <separator> <outputDirName>"
      )
      println("Example: data/writers.csv data/images.csv \";\" output")
      sys.exit(42)
    }
  }

  // ##########################
  // Case classes and functions
  // ##########################

  case class Image(writerFullname: String, path: String)
  case class Writer(
      quote: String,
      number: String,
      fullname: String,
      birth: Option[String],
      death: Option[String]
  )

  def log(x: Any) = {
    val now = LocalDateTime.now()
    println(s"$now - $x")
  }

  def processWriters(writersFile: File, separator: String): List[Writer] = {
    val tryWriters = decodeCsv(
      writersFile,
      separator,
      l =>
        Try {
          l match {
            case quote :: number :: fullname :: birth :: death :: Nil =>
              Writer(
                quote,
                number,
                fullname,
                Option.unless(birth.isEmpty)(birth),
                Option.unless(death.isEmpty)(death)
              )
            case _ => sys.error(s"Error in writers file, line: '$l'")
          }
        }
    )
    returnSuccessPrintFailures(tryWriters)
  }

  def processImages(imagesFile: File, separator: String): List[Image] = {
    val tryImages = decodeCsv(
      imagesFile,
      separator,
      l =>
        Try {
          l match {
            case fullname :: path :: Nil => Image(fullname, path)
            case _ => sys.error(s"Error in images file, line: '$l'")
          }
        }
    )
    returnSuccessPrintFailures(tryImages)
  }

  def decodeCsv[T](
      file: File,
      separator: String,
      decode: List[String] => T,
      dropFirstLine: Boolean = true
  ): List[T] = {
    val fileToList = file.lines.toList
    val ls = if (dropFirstLine) fileToList.drop(1) else fileToList
    ls.map(l => decode(l.split(separator, -1).toList))
  }

  def returnSuccessPrintFailures[T](tries: List[Try[T]]): List[T] = {
    val failures = tries.collect { case Failure(v) => v }
    if (failures.nonEmpty) {
      failures.foreach(println)
      sys.exit(42)
    }
    tries.collect { case Success(v) => v }
  }

  def checkImagesFullnamesExistence(
      images: List[Image],
      writers: List[Writer]
  ): List[Try[Any]] = {
    val writerFullnames = writers.map(_.fullname)
    images
      .map(_.writerFullname)
      .toSet[String]
      .filter(iwf => !writerFullnames.contains(iwf))
      .map(iwf => Try(sys.error(s"'$iwf' not exist in writers files")))
      .toList
  }

  def checkImagesPathsExistence(images: List[Image]): List[Try[Any]] = {
    images
      .filter(i => File(i.path).notExists)
      .map(i =>
        Try(
          sys.error(
            s"Image file '${i.path}' for writer '${i.writerFullname}' not exists"
          )
        )
      )
  }

  def mkdirsAndCopyAndXmlWriting(
      writers: List[Writer],
      images: List[Image],
      outputDirName: String
  ): Unit = {
    val writersMap = writers.groupBy(_.fullname).map { case (fullname, ws) =>
      (fullname, ws.head)
    }
    val imagesMap = images.groupBy(_.writerFullname)

    val outputDir =
      File(outputDirName).delete(true).createDirectoryIfNotExists()
    val outXmlFileName = s"$outputDirName/writers.xml"
    val xmlFile = File(outXmlFileName).delete(true).touch()

    for {
      (fullname, writer) <- writersMap
      imagesPaths <- imagesMap.get(fullname)
    } yield {
      val writerDirName = s"${writer.quote}_${writer.number}"
      log(s"Make dir '$writerDirName' if non exists")
      val writerDir = outputDir.createChild(writerDirName, true)
      imagesPaths.foreach(i => {
        log(s"Copy '${i.path}' to '$outputDirName/$writerDirName'")
        File(i.path).copyToDirectory(writerDir)
      })
      log(s"Append '${writer.fullname}' to '$outXmlFileName'")
      xmlAppend(writer, xmlFile)
    }
  }

  def xmlAppend(writer: Writer, xmlFile: File): Unit = {
    val dateElem = (writer.birth, writer.death) match {
      case (Some(b), Some(d)) =>
        s"<Isad313Dates normal=\"$b/$d\">$b-$d</Isad313Dates>"
      case (Some(b), None) =>
        s"<Isad313Dates normal=\"$b\">Né en $b</Isad313Dates>"
      case (None, Some(d)) =>
        s"<Isad313Dates normal=\"$d\">Mort en $d</Isad313Dates>"
      case (None, None) =>
        "<Isad313Dates normal=\"----\">Dates de vie inconnues</Isad313Dates>"
    }
    val xmlNode = s"""|<Pièce>
      |<Isad311Référence>${writer.quote}/${writer.number}</Isad311Référence>
      |<Isad312Intitulé>${writer.fullname}</Isad312Intitulé>
      |$dateElem
      |<Marc100Personnalité>${writer.fullname}</Marc100Personnalité>
      |</Pièce>""".stripMargin

    xmlFile.appendLine(xmlNode)
    ()
  }
}
