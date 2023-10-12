package ch.cjbg.lwm

import better.files._

import java.time.LocalDateTime
import scala.language.existentials
import scala.util.Failure
import scala.xml.PrettyPrinter

import File._

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
      val writers = decodeCsv(
        writersFile,
        separator,
        l =>
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
      )

      val images = decodeCsv(
        imagesFile,
        separator,
        l =>
          l match {
            case path :: fullname :: Nil => Image(path, fullname)
            case _ => sys.error(s"Error in images file, line: '$l'")
          }
      )

      log("Check fullames existence / matching in both files")
      checkImagesFullnamesExistence(images, writers)

      log("Check images paths existence")
      checkImagesPathsExistence(images)

      log("Make directories, copy images sources and write XML file")
      mkdirsAndCopyAndXmlWriting(writers, images, outputDirName)
    }
    case _ => {
      println(
        "Give args: <writersFileName> <imagesFileName> <separator> <outputDirName> <outXmlFileName>"
      )
      println("Example: data/writers.csv data/images.csv ; output out.xml")
      sys.exit(42)
    }
  }

  // ##########################
  // Case classes and functions
  // ##########################

  case class Image(path: String, writerFullname: String)
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

  def checkImagesFullnamesExistence(
      images: List[Image],
      writers: List[Writer]
  ): Unit = {
    val writerFullnames = writers.map(_.fullname)
    images
      .map(_.writerFullname)
      .toSet[String]
      .foreach(iwf =>
        if (!writerFullnames.contains(iwf))
          sys.error(s"'$iwf' not exist in writers files")
      )
  }

  def checkImagesPathsExistence(images: List[Image]): Unit = {
    images.foreach(i =>
      if (File(i.path).notExists)
        sys.error(
          s"Image file '${i.path}' for writer '${i.writerFullname}' not exists"
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
    val pp = new PrettyPrinter(120, 2)

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
      xmlAppend(writer, xmlFile, pp)
    }

    // TODO zip outputDir ?
    // outputDir.zipTo(File("output.zip"))
  }

  def xmlAppend(writer: Writer, xmlFile: File, pp: PrettyPrinter): Unit = {
    val dateElem = (writer.birth, writer.death) match {
      case (Some(b), Some(d)) => s"$b/$d"
      case (Some(b), None)    => b
      case (None, Some(d))    => d
      case (None, None)       => "..."
    }
    val xmlWriter = <node>
          <fullname>{writer.fullname}</fullname>
          <date>{dateElem}</date>
        </node>
    xmlFile.appendLine(pp.format(xmlWriter))
    ()
  }
}
