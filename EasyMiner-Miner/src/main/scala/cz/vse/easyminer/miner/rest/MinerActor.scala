/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.miner.rest

import java.util.UUID

import akka.actor._
import cz.vse.easyminer.core.UnexpectedActorRequest
import cz.vse.easyminer.miner._
import org.slf4j.LoggerFactory

import scala.collection.immutable.Queue
import scala.concurrent.duration._
import scala.language.postfixOps

class MinerActor private(id: UUID) extends Actor with FSM[MinerActor.State, MinerActor.Data] {

  import MinerActor._

  val starttime = System.currentTimeMillis
  val logger = LoggerFactory.getLogger("cz.vse.easyminer.miner.rest.MinerActor")

  context.setReceiveTimeout(2 minutes)

  startWith(State.InProgress, Data.PartialResult(Queue.empty, None))

  when(State.InProgress) {
    case Event(Request.GetResult(true), Data.PartialResult(parts, Some(result))) if parts.isEmpty =>
      logger.debug(s"$id: Request.GetResult -> State.Finished, Data.Result(rules), Response.Result")
      goto(State.Finished) using Data.Result(result) replying Response.Result(result)
    case Event(Request.GetResult(true), Data.PartialResult(parts, None)) if parts.isEmpty =>
      logger.debug(s"$id: Request.GetResult -> Response.InProgress")
      stay replying Response.InProgress
    case Event(Request.GetResult(true), data: Data.PartialResult) if data.parts.nonEmpty =>
      logger.debug(s"$id: Request.GetResult -> Data.PartialResult, Response.PartialResult")
      val mergedPartialResults = data.parts.reduce((p1, p2) => MinerResult(p1.task, p1.headers ++ p2.headers, p1.rules ++ p2.rules))
      stay using data.copy(parts = Queue.empty) replying Response.PartialResult(mergedPartialResults)
    case Event(Request.PostError(th), _) =>
      logger.debug(s"$id: Request.PostError -> State.Finished, Data.Error($th)")
      goto(State.Finished) using Data.Error(th)
    case Event(Request.PostPartialResult(result), data: Data.PartialResult) =>
      logger.debug(s"$id: Request.PostPartialResult -> Data.PartialResult")
      stay using data.copy(parts = data.parts.enqueue(result))
    case Event(Request.PostResult(result), data: Data.PartialResult) =>
      logger.debug(s"$id: Request.PostResult -> Data.PartialResult")
      stay using data.copy(result = Some(result))
  }

  when(State.Finished) {
    case Event(Request.GetResult(false), Data.Result(result)) =>
      logger.debug(s"$id: Request.GetResult -> Response.Result, stop")
      logger.info(s"$id: mining result picking up in ${System.currentTimeMillis - starttime}ms")
      stop replying Response.Result(result)
    case Event(_: Request.GetResult, Data.Error(th)) =>
      logger.debug(s"$id: Request.GetResult -> Response.Error($th), stop")
      stop replying Status.Failure(th)
  }

  whenUnhandled {
    case Event(ReceiveTimeout, _) =>
      logger.debug(s"$id: Request.ReceiveTimeout -> stop")
      stop()
    case Event(_: Request.GetResult, _) =>
      logger.debug(s"$id: Request.ResultRequest -> unexpected request")
      stay replying Status.Failure(UnexpectedActorRequest)
    case Event(_, _) =>
      logger.warn(s"$id: Request.Undefined -> stop")
      stop()
  }

}

object MinerActor {

  def props(id: UUID) = Props(new MinerActor(id))

  sealed trait Response

  object Response {

    object InProgress extends Response

    case class PartialResult(result: MinerResult) extends Response

    case class Result(result: MinerResult) extends Response

  }

  sealed trait Request

  object Request {

    case class PostPartialResult(result: MinerResult) extends Request

    case class PostResult(result: MinerResult) extends Request

    case class PostError(th: Throwable) extends Request

    case class GetResult(isPartial: Boolean) extends Request

  }

  sealed trait State

  object State {

    object InProgress extends State

    object Finished extends State

  }

  sealed trait Data

  object Data {

    case class Error(ex: Throwable) extends Data

    case class PartialResult(parts: Queue[MinerResult], result: Option[MinerResult]) extends Data

    case class Result(data: MinerResult) extends Data

  }

}