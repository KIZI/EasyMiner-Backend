package cz.vse.easyminer.data.impl

import akka.actor._
import cz.vse.easyminer.core.util.FSMWithExceptionHandler
import cz.vse.easyminer.data.{BufferedWriter, DataSourceDetail, DataSourceOps}
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
  * Created by propan on 24. 7. 2015.
  */
class UploadActor(id: String,
                  bufferedWriter: BufferedWriter,
                  futureDataSource: Future[DataSourceDetail],
                  dataSourceOps: DataSourceOps) extends Actor with FSMWithExceptionHandler[UploadActor.State, UploadActor.Data] {

  import UploadActor._

  context.setReceiveTimeout(30 seconds)

  startWith(State.InProgress, Data.FileSize(0))

  implicit val ec = context.dispatcher

  val logger = LoggerFactory.getLogger("cz.vse.easyminer.data.impl.UploadActor")

  def handleFutureInProgress(f: => State) = futureDataSource.value match {
    case None => f
    case Some(Failure(ex)) => stop() replying Status.Failure(ex)
    case Some(Success(result)) =>
      rollback(result)
      stop() replying Status.Failure(Exceptions.ResultIsTooSoon)
  }

  def handleException(ex: Exception): State = {
    bufferedWriter.finish()
    futureDataSource.onSuccess {
      case result => rollback(result)
    }
    stop replying Status.Failure(ex)
  }

  def stoppingLog() = logger.debug(s"$id: The uploaded task is now stopping...")

  def rollback(dataSource: DataSourceDetail) = {
    logger.debug(s"$id: Rollback the whole upload task.")
    dataSourceOps.deleteDataSource(dataSource.id)
  }

  when(State.InProgress) {
    exceptionHandling {
      case Event(Request.Data(chunk), Data.FileSize(size)) =>
        val isWritten = bufferedWriter.write(chunk)
        handleFutureInProgress {
          if (isWritten) {
            val newFileSize = size + chunk.length
            logger.debug(s"$id: Incomming data chunk with length: " + (chunk.length / 1000.0) + "kB, total: " + (newFileSize / 1000000.0) + "MB")
            stay using Data.FileSize(newFileSize) replying Response.InProgress
          } else {
            stay replying Response.SlowDown
          }
        }
      case Event(Request.Finish, Data.FileSize(size)) =>
        handleFutureInProgress {
          logger.debug(s"$id: The file has been successfully uploaded; Final size: " + (size / 1000000.0) + "MB")
          bufferedWriter.finish()
          goto(State.Finished) using Data.NoData replying Response.InProgress
        }
    }
  }

  when(State.Finished) {
    case Event(_: Request, _) => futureDataSource.value match {
      case Some(Success(result)) => stop replying Response.Finished(result)
      case Some(Failure(ex)) => stop replying Status.Failure(ex)
      case None => stay replying Response.InProgress
    }
  }

  whenUnhandled {
    case Event(ReceiveTimeout, _) =>
      logger.debug(s"$id: Timeout during file uploading.")
      bufferedWriter.finish()
      futureDataSource.onComplete {
        case Success(result) => rollback(result)
        case Failure(ex) =>
          logger.debug(s"$id: The insert task has failed with message: ${ex.getMessage}")
          throw ex
      }
      stop()
  }

  onTermination {
    case StopEvent(FSM.Normal, _, _) => stoppingLog()
  }

}

object UploadActor {

  def props(id: String,
            bufferedWriter: BufferedWriter,
            futureDataSource: Future[DataSourceDetail],
            dataSourceOps: DataSourceOps) = Props(new UploadActor(id, bufferedWriter, futureDataSource, dataSourceOps))

  sealed trait Request

  object Request {

    case class Data(chunk: Array[Byte]) extends Request

    object Finish extends Request

  }

  sealed trait Response

  object Response {

    case class Finished(dataSource: DataSourceDetail) extends Response

    object InProgress extends Response

    object SlowDown extends Response

  }

  sealed trait State

  object State {

    object InProgress extends State

    object Finished extends State

  }

  sealed trait Data

  object Data {

    object NoData extends Data

    case class FileSize(size: Int) extends Data

  }

  object Exceptions {

    object ResultIsTooSoon extends Exception("Future result has been finished too soon without any exception during the uploading process. This is an unexpected behaviour.")

  }

}