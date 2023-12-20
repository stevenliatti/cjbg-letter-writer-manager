val version = "0.3.0"

ThisBuild / scalaVersion     := "2.13.11"
ThisBuild / organization     := "ch.cjbg"
ThisBuild / organizationName := "cjbg"

lazy val root = (project in file("."))
  .settings(
    name := "Letter Writer Manager",
    scalacOptions ++= Seq("-deprecation", "-feature"),
    assembly / mainClass := Some("ch.cjbg.lwm.Main"),
    assembly / assemblyJarName := s"lwm-$version.jar",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % "0.7.29" % Test,
      "com.github.pathikrit" %% "better-files" % "3.9.2"
    )
  )
