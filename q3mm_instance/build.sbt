name := "q3mm_instance"

version := "1.0-SNAPSHOT"

mainClass := Some("Main")

scalaVersion := "2.11.7"

libraryDependencies ++=
  Seq("com.typesafe.akka" %% "akka-actor" % "2.4.17",
    "com.typesafe.akka" %% "akka-remote" % "2.4.17",
    "org.zeromq" % "jzmq" % "3.1.0")


import NativePackagerHelper._

enablePlugins(JavaServerAppPackaging)

mappings in Universal += {
  // we are using the reference.conf as default application.conf
  // the user can override settings here
  val conf = (resourceDirectory in Compile).value / "application.conf"
  conf -> "application.conf"
}


