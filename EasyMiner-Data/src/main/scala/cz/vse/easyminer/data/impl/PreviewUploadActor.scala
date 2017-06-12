/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data.impl

import akka.actor.{Actor, FSM, ReceiveTimeout, Status}
import cz.vse.easyminer.core.util.BasicFunction._
import cz.vse.easyminer.core.util.FSMWithExceptionHandler
import cz.vse.easyminer.data.BufferedWriter
import cz.vse.easyminer.data.impl.PreviewUploadActor._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
  * Created by Vaclav Zeman on 5. 9. 2015.
  */

/**
  * Actor for control preview uploading.
  * This receives data buckets and send it into an input stream.
  * After the preview uploader obtains some sample of data with a specific size we return this sample and stop this actor
  *
  * @param id             upload id
  * @param bufferedWriter writer to which we will send data buckets
  * @param futureData     future object of the result
  */
class PreviewUploadActor(id: String,
                         bufferedWriter: BufferedWriter,
                         futureData: Future[Array[Byte]]) extends Actor with FSMWithExceptionHandler[PreviewUploadActor.State, PreviewUploadActor.Data] {

  /**
    * After 30 seconds there are no arriving data we stop this actor
    */
  context.setReceiveTimeout(30 seconds)

  startWith(State.InProgress, Data.NoData)

  def handleFuture(f: => FSM.State[PreviewUploadActor.State, PreviewUploadActor.Data]) = futureData.value match {
    case Some(Success(result)) => stop replying Response.Finished(result)
    case Some(Failure(ex)) => stop replying Status.Failure(ex)
    case None => f
  }

  def handleException(ex: Exception): State = {
    stop replying Status.Failure(ex)
  }

  when(State.InProgress) {
    //if some exception we stop this actor and send back the exception
    exceptionHandling {
      //if we receive some data we first check whether the future result is completed (the preview is sufficient)
      //if not then we write data to the writer
      case Event(Request.Data(data), _) => handleFuture {
        limitedRepeatUntil[Boolean](100)(x => x)(bufferedWriter.write(data)) match {
          case true => stay replying Response.InProgress
          case false => throw Exceptions.NoWrittenChunk
        }
      }
      //if we receive end of stream then we check result and return it
      //if the result is not completed then end the stream, go to finished state and return in progress state
      case Event(Request.Finish, _) => handleFuture {
        bufferedWriter.finish()
        goto(State.Finished) replying Response.InProgress
      }
    }
  }

  when(State.Finished) {
    //wait for a result
    //if there is no result return in progress state
    case Event(_: Request, _) => handleFuture {
      stay replying Response.InProgress
    }
  }

  whenUnhandled {
    case Event(ReceiveTimeout, _) =>
      bufferedWriter.finish()
      stop()
  }

}

object PreviewUploadActor {

  object Exceptions {

    object NoWrittenChunk extends Exception("The last chunk could not be written and parsed. The input buffer is probably full.")

  }

  sealed trait Request

  object Request {

    case class Data(chunk: Array[Byte]) extends Request

    object Finish extends Request

  }

  sealed trait Response

  object Response {

    case class Finished(data: Array[Byte]) extends Response

    object InProgress extends Response

  }

  sealed trait State

  object State {

    object InProgress extends State

    object Finished extends State

  }

  sealed trait Data

  object Data {

    object NoData extends Data

  }

}