package cz.vse.easyminer.miner.impl.spark

import java.io._
import java.util.UUID

import cz.vse.easyminer.core.db.{HiveDBConnector, MysqlDBConnector}
import cz.vse.easyminer.core.hadoop.Yarn
import cz.vse.easyminer.core.hadoop.Yarn.Implicits._
import cz.vse.easyminer.core.util.BasicFunction._
import cz.vse.easyminer.core.util._
import cz.vse.easyminer.data.hadoop.DataHdfs
import cz.vse.easyminer.miner.MinerResultHeader.MiningTime
import cz.vse.easyminer.miner.impl.io.PmmlTaskBuilder
import cz.vse.easyminer.miner.impl.r.AruleExtractor
import cz.vse.easyminer.miner.impl.spark.FpGrowthMinerProcess.Exceptions.{OutputFileDoesNotExist, SparkMiningError, Timeout}
import cz.vse.easyminer.miner.{Count, MinerProcess, MinerResult, MinerTask}
import cz.vse.easyminer.preprocessing.DatasetDetail
import cz.vse.easyminer.preprocessing.impl.db.hive.{HiveAttributeOps, Tables}
import org.apache.spark.launcher.SparkAppHandle.{Listener, State}
import org.apache.spark.launcher.{SparkAppHandle, SparkLauncher}
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source
import scala.language.postfixOps

/**
  * Created by propan on 1. 3. 2016.
  */
trait FpGrowthMinerProcess extends MinerProcess {

  implicit val mysqlDBConnector: MysqlDBConnector
  implicit val hiveDBConnector: HiveDBConnector

  private val logger = LoggerFactory.getLogger("cz.vse.easyminer.miner.impl.spark.FpGrowthMinerProcess")
  private val maxMiningTime = Conf().get[Duration]("easyminer.miner.spark.max-mining-time").toSeconds.toInt
  private val maxStoppingTime = Conf().get[Duration]("easyminer.miner.spark.max-stopping-time").toSeconds.toInt

  private class HivePmmlTaskBuilder(val templateParameters: Map[String, Any] = Map.empty) extends PmmlTaskBuilder {
    def apply(templateParameters: Map[String, Any]): PmmlTaskBuilder = new HivePmmlTaskBuilder(templateParameters)

    def withMiningTime(maxMiningTime: Int) = apply(templateParameters + ("max-mining-time" -> maxMiningTime))

    protected[this] def datasetToInstanceTable(dataset: DatasetDetail) = new Tables.InstanceTable(dataset.id)
  }

  private class SparkProcessHandle(taskId: UUID, process: Process) extends SparkAppHandle {

    private implicit val ec = ExecutionContext.global

    private val startTime = System.currentTimeMillis()

    private object Lock

    private var isFinished = false
    private var state = State.UNKNOWN
    private var appId = ""
    private var startMiningTime = 0L
    private var endMiningTime = 0L

    Future {
      tryClose(new BufferedReader(new InputStreamReader(process.getErrorStream))) { reader =>
        val AppId = "Submitted application (application_\\w+)$".r.unanchored
        val AppState = "\\(state: ([A-Z]+)\\)".r.unanchored
        val AppStateFinal = "final status: ([A-Z]+)$".r.unanchored
        Stream.continually(reader.readLine()).takeWhile(_ != null).foreach { line =>
          logger.trace(s"${taskId.toString}: $line")
          Lock synchronized Match(line) {
            case AppId(id) => Lock synchronized {
              logger.debug(s"${taskId.toString}: Mining task submitted with id '$id'.")
              state = State.SUBMITTED
              appId = id
            }
            case AppState(status) if !isFinished => Lock synchronized {
              Match(status) {
                case "RUNNING" =>
                  state = State.RUNNING
                  if (startMiningTime == 0) startMiningTime = System.currentTimeMillis()
                case "FINISHED" | "FAILED" =>
                  logger.debug(s"${taskId.toString}: Mining task has been finished.")
                  isFinished = true
                  if (endMiningTime == 0) endMiningTime = System.currentTimeMillis()
              }
            }
            case AppStateFinal(status) if isFinished => Lock synchronized {
              status match {
                case "SUCCEEDED" =>
                  logger.debug(s"${taskId.toString}: Mining task has been successful.")
                  state = State.FINISHED
                case _ =>
                  logger.debug(s"${taskId.toString}: Mining task has failed.")
                  state = State.FAILED
              }
            }
          }
        }
        if (!isFinished) kill()
      }
    }

    def stop(): Unit = if (process.isAlive) process.destroyForcibly().waitFor()

    def disconnect(): Unit = {
      stop()
    }

    def kill(): Unit = Lock synchronized {
      stop()
      Yarn.applicationId(appId).foreach { applicationId =>
        if (!Yarn.applicationStateIsFinal(applicationId)) {
          logger.debug(s"${taskId.toString}: Spark mining task is killing...")
          Yarn.kill(applicationId)
          val finalState = limitedRepeatUntil(maxStoppingTime, 1 second)(Yarn.applicationStateIsFinal)(Yarn.applicationState(applicationId))
          if (Yarn.applicationStateIsFinal(finalState)) {
            logger.debug(s"${taskId.toString}: Spark mining task has been killed with status: " + finalState)
          } else {
            logger.warn(s"${taskId.toString}: Spark mining task killing has not been successful: " + finalState)
          }
        }
      }
      state = State.KILLED
    }

    def getState: State = state

    def addListener(l: Listener): Unit = {}

    def getAppId: String = appId

    def preMiningTime: Duration = if (startMiningTime == 0) Duration.Zero else (startMiningTime - startTime) millis

    def miningTime: Duration = if (endMiningTime == 0 || preMiningTime.toMillis == 0) Duration.Zero else (endMiningTime - startTime - preMiningTime.toMillis) millis
  }

  implicit private class HdfsFileOps(fileName: String)(implicit hdfs: DataHdfs) {
    def toHdfsPath = hdfs.filePath(fileName).toString

    def toHdfsUri = "hdfs://" + hdfs.filePath(fileName).toString

    def toHdfsLibUri = "hdfs://" + hdfs.filePath("lib/" + fileName).toString
  }

  private def sparkLauncher(inputFileName: String)(implicit hdfs: DataHdfs) = {
    val minerJar = Conf().get[String]("easyminer.miner.spark.miner-jar").toHdfsUri
    val hiveSiteFile = new File(Conf().get[String]("easyminer.miner.spark.hive-site-file-path")).getAbsolutePath
    val logFile = new File(Conf().get[String]("easyminer.miner.spark.log-file-path")).getAbsolutePath
    val libJars = Conf().get[List[String]]("easyminer.miner.spark.lib-jars").iterator.map(_.toHdfsLibUri)
    val assemblyJar = Conf().get[String]("easyminer.miner.spark.assembly-jar").toHdfsLibUri
    val launcher = new SparkLauncher()
      .setAppResource(minerJar)
      .setMainClass("cz.jkuchar.easyminer.sparkminer.MinerLauncher")
      .addAppArgs(inputFileName.toHdfsPath)
      .setMaster("yarn")
      .setDeployMode("cluster")
      .addFile(hiveSiteFile)
      .addFile(logFile)
      .setConf("spark.yarn.jar", assemblyJar)
    libJars.foreach(launcher.addJar)
    launcher
  }

  def process(mt: MinerTask)(processListener: (MinerResult) => Unit): MinerResult = DataHdfs { implicit hdfs =>
    val startTime = System.currentTimeMillis()
    val taskId = UUID.randomUUID()
    val inputFileName = "mining-task-" + taskId.toString + ".xml"
    val outputFileName = "mining-task-" + taskId.toString + ".output.csv"
    logger.debug(s"Spark mining task in PMML '$inputFileName' is uploading to HDFS...")
    tryClose {
      val pmml = new HivePmmlTaskBuilder().withMiningTime(maxMiningTime).withMinerTask(mt).withDatabaseName(hiveDBConnector.dbSettings.dbName).toPmml
      logger.trace("Hadoop pmml:\n" + pmml)
      new ByteArrayInputStream(pmml.getBytes)
    } { bais =>
      hdfs.putFile(inputFileName, bais)
    }
    logger.debug(s"${taskId.toString}: Spark mining has been started...")
    val preparingTime = (System.currentTimeMillis() - startTime) millis
    val sparkAppHandle = new SparkProcessHandle(taskId, sparkLauncher(inputFileName).launch())
    val finalTaskState = limitedRepeatUntil[SparkAppHandle](maxMiningTime, 1 second)(_.getState.isFinal)(sparkAppHandle).getState
    try {
      if (finalTaskState == SparkAppHandle.State.FINISHED) {
        val startTime = System.currentTimeMillis()
        if (!hdfs.fileExists(outputFileName)) throw OutputFileDoesNotExist
        hdfs.readFile(outputFileName) { is =>
          val attributes = HiveAttributeOps(mt.datasetDetail).getAllAttributes
          val rules = Source.fromInputStream(is).getLines().collect(AruleExtractor.getOutputARuleMapper(Count(mt.datasetDetail.size), attributes)).toList
          val finishingTime = (System.currentTimeMillis() - startTime).millis
          MinerResult(
            mt,
            Set(MiningTime(sparkAppHandle.preMiningTime + preparingTime, sparkAppHandle.miningTime, finishingTime)),
            rules
          )
        }
      } else if (!finalTaskState.isFinal) {
        sparkAppHandle.kill()
        Thread.sleep(1000)
        throw Timeout
      } else {
        throw SparkMiningError
      }
    } finally {
      hdfs.deleteFile(inputFileName)
      hdfs.deleteFile(outputFileName)
      Iterator(".output.xml", ".log", ".json").map("mining-task-" + taskId.toString + _).foreach(hdfs.deleteFile)
      sparkAppHandle.disconnect()
    }
  }

}

object FpGrowthMinerProcess {

  object Exceptions {

    object OutputFileDoesNotExist extends Exception("Spark mining has been finished, but any output result does not appear in the HDFS working directory.")

    object SparkMiningError extends Exception("Spark mining process has been finished with an error.")

    object Timeout extends Exception("The Spark mining process has not been finished yet. Timeout has been reached and the mining process was killed.")

  }

}