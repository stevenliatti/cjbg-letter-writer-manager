package ch.cjbg.lwm

import better.files._

import scala.language.existentials
import scala.util.Failure
import scala.xml.PrettyPrinter

import File._

case class Image(path: String, writerFullname: String)
case class Writer(
    quote: String,
    number: String,
    fullname: String,
    birth: Option[String],
    death: Option[String]
)

object Main extends App {
  args.toList match {
    case writersFileName :: imagesFileName :: separator :: outputDirName :: outXmlFileName :: Nil => {
      println(
        s"writersFileName: '$writersFileName', imagesFileName: '$imagesFileName', separator: '$separator', outputDirName: '$outputDirName', outXmlFileName: '$outXmlFileName'"
      )

      val writersFile = File(writersFileName)
      val imagesFile = File(imagesFileName)

      val writers = writersFile.lines.toList
        .drop(1)
        .map(l =>
          l.split(separator, -1).toList match {
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

      val images = imagesFile.lines.toList
        .drop(1)
        .map(l =>
          l.split(separator, -1).toList match {
            case path :: fullname :: Nil => Image(path, fullname)
            case _ => sys.error(s"Error in images file, line: '$l'")
          }
        )

      val writerFullnames = writers.map(_.fullname)
      images
        .map(_.writerFullname)
        .toSet[String]
        .foreach(iwf =>
          if (!writerFullnames.contains(iwf))
            sys.error(s"'$iwf' not exist in writers files")
        )

      images.foreach(i =>
        if (File(i.path).notExists)
          sys.error(
            s"Image file '${i.path}' for writer '${i.writerFullname}' not exists"
          )
      )

      val writersMap = writers.groupBy(_.fullname).map { case (fullname, ws) =>
        (fullname, ws.head)
      }
      val imagesMap = images.groupBy(_.writerFullname)

      val outputDir =
        File(outputDirName).delete(true).createDirectoryIfNotExists()
      val xmlFile = File(outXmlFileName).delete(true).touch()
      val pp = new PrettyPrinter(120, 2)

      for {
        (fullname, writer) <- writersMap
        imagesPaths <- imagesMap.get(fullname)
      } yield {
        val writerDir =
          outputDir.createChild(s"${writer.quote}_${writer.number}", true)
        imagesPaths.foreach(i => File(s"${i.path}").copyToDirectory(writerDir))

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
      }
    }
    case _ => {
      println(
        "Give args: <writersFileName> <imagesFileName> <separator> <outputDirName> <outXmlFileName>"
      )
      println("Example: data/writers.csv data/images.csv ; output out.xml")
      sys.exit(42)
    }
  }
  // outputDir.zipTo(File("output.zip"))
}
