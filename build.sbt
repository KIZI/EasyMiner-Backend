import com.github.retronym.SbtOneJar
import xerial.sbt.Pack._

name := "EasyMiner"

val akkaV = "2.3.9"
val sprayV = "1.3.3"
val scalikejdbcV = "2.4.0"
val slf4jV = "1.7.7"
val jenaV = "3.1.1"

val shapeless = "com.chuusai" %% "shapeless" % "2.1.0"
val sprayCan = "io.spray" %% "spray-can" % sprayV
val sprayRouting = "io.spray" %% "spray-routing-shapeless2" % sprayV
val sprayClient = "io.spray" %% "spray-client" % sprayV
val sprayTest = "io.spray" %% "spray-testkit" % sprayV % "test"
val sprayJson = "io.spray" %% "spray-json" % "1.3.2"
val akkaActor = "com.typesafe.akka" %% "akka-actor" % akkaV
val akkaLogging = "com.typesafe.akka" %% "akka-slf4j" % akkaV
val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.1.2"
val slf4jSimple = "org.slf4j" % "slf4j-simple" % slf4jV
val log4j = "org.slf4j" % "log4j-over-slf4j" % slf4jV
val rEngine = "org.nuiton.thirdparty" % "REngine" % "1.7-3"
val rServe = "org.nuiton.thirdparty" % "Rserve" % "1.7-3"
val mysqlJdbc = "mysql" % "mysql-connector-java" % "5.1.33"
val scalaJdbc = "org.scalikejdbc" %% "scalikejdbc" % scalikejdbcV
val configs = "com.github.kxbmap" %% "configs" % "0.2.2"
val scalaTest = "org.scalatest" %% "scalatest" % "2.2.4" % "test"
val scalate = "org.scalatra.scalate" %% "scalate-core" % "1.7.0"
val apacheCompress = "org.apache.commons" % "commons-compress" % "1.9"
val jena = "org.apache.jena" % "jena-arq" % jenaV

val basicDependencies = Seq(shapeless, scalaLogging, akkaActor, akkaLogging, sprayCan, sprayRouting, sprayJson, sprayClient, slf4jSimple, log4j, configs, mysqlJdbc, scalaJdbc)
val testRestDependencies = Seq(scalaTest, sprayTest)
val rDependencies = Seq(rServe, rEngine)

val basicSettings = Seq(
  organization := "cz.vse.easyminer",
  version := "1.0",
  scalaVersion := "2.11.7",
  scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-encoding", "utf8"),
  exportJars := true,
  resolvers += "conjars.org" at "http://conjars.org/repo/",
  scalacOptions ++= Seq("-Xmax-classfile-name","78")
) ++ net.virtualvoid.sbt.graph.Plugin.graphSettings ++ packSettings ++ SbtOneJar.oneJarSettings

lazy val root = project
  .in(file("."))
  .settings(basicSettings: _*)
  .aggregate(core, data, preprocessing, miner)

lazy val core = project
  .in(file("EasyMiner-Core"))
  .settings(basicSettings: _*)
  .settings(libraryDependencies ++= (basicDependencies ++ Seq(scalaTest, scalate)))
  .settings(parallelExecution in Test := false)

lazy val data = project
  .in(file("EasyMiner-Data"))
  .configs()
  .settings(basicSettings: _*)
  .settings(libraryDependencies ++= (basicDependencies ++ testRestDependencies ++ Seq(scalate, apacheCompress, jena)))
  .settings(parallelExecution in Test := false)
  .settings(scalateSettings ++ Seq(
    ScalateKeys.scalateOverwrite := true,
    ScalateKeys.scalateTemplateConfig in Compile := Seq(TemplateConfig(file("EasyMiner-Data") / "src" / "main" / "resources", Nil, Nil, Some("")))
  ))
  .settings(Seq(packMain := Map("main" -> "cz.vse.easyminer.data.Main")))
  .dependsOn(core)

lazy val preprocessing = project
  .in(file("EasyMiner-Preprocessing"))
  .configs()
  .settings(basicSettings: _*)
  .settings(libraryDependencies ++= (basicDependencies ++ testRestDependencies ++ Seq(scalate)))
  .settings(parallelExecution in Test := false)
  .settings(scalateSettings ++ Seq(
    ScalateKeys.scalateOverwrite := true,
    ScalateKeys.scalateTemplateConfig in Compile := Seq(TemplateConfig(file("EasyMiner-Preprocessing") / "src" / "main" / "resources", Nil, Nil, Some("")))
  ))
  .settings(Seq(packMain := Map("main" -> "cz.vse.easyminer.preprocessing.Main")))
  .dependsOn(data)

lazy val miner = project
  .in(file("EasyMiner-Miner"))
  .settings(basicSettings: _*)
  .settings(libraryDependencies ++= (basicDependencies ++ testRestDependencies ++ rDependencies ++ Seq(scalate)))
  .settings(parallelExecution in Test := false)
  .settings(testOptions in Test += Tests.Argument("-oF"))
  .settings(scalateSettings ++ Seq(
    ScalateKeys.scalateOverwrite := true,
    ScalateKeys.scalateTemplateConfig in Compile := Seq(TemplateConfig(file("EasyMiner-Miner") / "src" / "main" / "resources", Nil, Nil, Some("")))
  ))
  .settings(Seq(packMain := Map("main" -> "cz.vse.easyminer.miner.Main")))
  .dependsOn(preprocessing)