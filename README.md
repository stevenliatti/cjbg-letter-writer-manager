# Letter Writer Manager

Little program who read writers and images CSV files and produce a file tree of images grouped by writers and an XML file of writers.

Compile the Scala program with SBT (`sbt compile && sbt assembly`) and run the produced `jar` with `java`.

Example : `java -jar lwm-0.1.0.jar data/writers.csv data/images.csv ";" output`
