name := "EasyMiner-Task"

organization := "cz.vse.easyminer"

version := "2.5"

scalaVersion := "2.11.7"

scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-encoding", "utf8")

val akkaV = "2.3.9"

val akkaActor = "com.typesafe.akka" %% "akka-actor" % akkaV

libraryDependencies += akkaActor