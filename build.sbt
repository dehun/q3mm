name := "q3mm"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)
lazy val q3mm_queue = project.dependsOn(root)
lazy val q3mm_instanceMaster = project.dependsOn(root)
lazy val q3mm_instance = project.dependsOn(root)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % Test,
  "com.typesafe.akka" %% "akka-actor" % "2.4.17",
  "com.typesafe.akka" %% "akka-remote" % "2.4.17",
  "org.zeromq" % "jzmq" % "3.1.0"
)
