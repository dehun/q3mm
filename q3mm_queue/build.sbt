
name := "q3mm_queue"

version := "1.0-SNAPSHOT"

mainClass := Some("Main")

scalaVersion := "2.11.7"

libraryDependencies ++=
  Seq("com.typesafe.akka" %% "akka-actor" % "2.4.17",
    "com.typesafe.akka" %% "akka-remote" % "2.4.17",
    "com.typesafe.akka" %% "akka-http-core" % "10.0.4",
    "com.typesafe.akka" %% "akka-http" % "10.0.4")

import NativePackagerHelper._

enablePlugins(JavaServerAppPackaging)

mappings in Universal += {
  // we are using the reference.conf as default application.conf
  // the user can override settings here
  val conf = (resourceDirectory in Compile).value / "application.conf"
  conf -> "application.conf"
}


