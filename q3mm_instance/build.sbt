name := "q3mm_instance"

version := "1.0-SNAPSHOT"

mainClass := Some("Main")

scalaVersion := "2.11.7"

libraryDependencies ++=
  Seq("com.typesafe.akka" %% "akka-actor" % "2.4.17",
    "com.typesafe.akka" %% "akka-remote" % "2.4.17")