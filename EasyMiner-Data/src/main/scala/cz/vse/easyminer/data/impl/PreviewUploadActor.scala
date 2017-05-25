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
class PreviewUploadActor(id: String,
                         bufferedWriter: BufferedWriter,
                         futureData: Future[Array[Byte]]) extends Actor with FSMWithExceptionHandler[PreviewUploadActor.State, PreviewUploadActor.Data] {

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
    exceptionHandling {
      case Event(Request.Data(data), _) => handleFuture {
        limitedRepeatUntil[Boolean](100)(x => x)(bufferedWriter.write(data)) match {
          case true => stay replying Response.InProgress
          case false => throw Exceptions.NoWrittenChunk
        }
      }
      case Event(Request.Finish, _) => handleFuture {
        bufferedWriter.finish()
        goto(State.Finished) replying Response.InProgress
      }
    }
  }

  when(State.Finished) {
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