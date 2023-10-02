package ch.cjbg.lwm

import better.files._

import File._

case class Image(path: String, writerId: String)
case class Writer(
    id: String,
    slugname: String,
    lastname: String,
    forname: String,
    birth: String,
    death: String
)

object Main extends App {
  val imagesFile = File("data/images.csv")
  val writersFile = File("data/writers.csv")

  val images = imagesFile.lines.toList
    .drop(1)
    .map(l =>
      l.split(",").toList match {
        case path :: id :: Nil => Image(path, id)
        case _                 => sys.exit(42)
      }
    )

  val writers = writersFile.lines.toList
    .drop(1)
    .map(l =>
      l.split(",").toList match {
        case id :: slugname :: lastname :: forname :: birth :: death :: Nil =>
          Writer(id, slugname, lastname, forname, birth, death)
        case _ => sys.exit(42)
      }
    )

  if (writers.map(_.id).size != writers.map(_.id).toSet.size) {
    println("Non unique ids in writers file")
    sys.exit(42)
  }
  if (images.map(_.writerId).toSet != writers.map(_.id).toSet) {
    println("Not all ids match in two files")
    sys.exit(42)
  }

  val imagesMap = images.groupBy(_.writerId)
  val writersMap = writers.groupBy(_.id).map { case (id, ws) => (id, ws.head) }
  val outputDir = File("output").createDirectoryIfNotExists()
  val xmlFile = File("out.xml").touch()

  for {
    (writerId, writer) <- writersMap
    imagesPaths <- imagesMap.get(writerId)
  } yield {
    val writerDir = outputDir.createChild(s"$writerId-${writer.slugname}", true)
    imagesPaths.foreach(i => File(s"${i.path}").copyToDirectory(writerDir))

    val xmlWriter = <node>
        <lastname>{writer.lastname}</lastname>
        <forname>{writer.forname}</forname>
        <date>{writer.birth}/{writer.death}</date>
      </node>
    xmlFile.appendText(xmlWriter.toString())
  }

  // outputDir.zipTo(File("output.zip"))
}
