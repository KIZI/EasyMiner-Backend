/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.core

import java.util.UUID

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import cz.vse.easyminer.core.TaskStatusActor.{Data, Request, Response, State}
import spray.http.{HttpHeaders, StatusCodes, Uri}
import spray.httpx.SprayJsonSupport
import spray.json._
import spray.routing.Directives

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

/**
 * Created by Vaclav Zeman on 9. 2. 2016.
 */
sealed trait TaskStatus {
  val id: UUID
  val name: String
}

case class EmptyTaskStatus(id: UUID, name: String) extends TaskStatus

case class MessageTaskStatus(id: UUID, name: String, message: String) extends TaskStatus

case class ResultTaskStatus[T](id: UUID, name: String, result: T) extends TaskStatus

object EmptyOrMessageTaskStatus {

  def unapply(taskStatus: TaskStatus): Option[TaskStatus] = taskStatus match {
    case _: EmptyTaskStatus | _: MessageTaskStatus => Some(taskStatus)
    case _ => None
  }

}

class TaskStatusActor[T](id: UUID, name: String, waitForResultRequest: Boolean) extends Actor with FSM[TaskStatusActor.State, TaskStatusActor.Data] {

  implicit val ec: ExecutionContext = context.dispatcher

  context.setReceiveTimeout(1 hour)

  startWith(State.InProgress, Data.NoData)

  when(State.InProgress) {
    case Event(Request.GetStatus, Data.NoData) => stay replying Response.Status(EmptyTaskStatus(id, name))
    case Event(Request.GetStatus, Data.Status(msg)) => stay replying Response.Status(MessageTaskStatus(id, name, msg))
    case Event(Request.PostStatus(msg), _) => stay using Data.Status(msg)
    case Event(Request.PostResult(result: Try[T]), _) => goto(State.Finished) using Data.Result(result)
    case Event(Request.GetResult, _) => stay replying Response.NoResult
  }

  when(State.Finished) {
    case Event(Request.GetStatus, Data.Result(Success(result))) =>
      val status = Response.Status(ResultTaskStatus(id, name, result.asInstanceOf[T]))
      if (waitForResultRequest) stay replying status else stop replying status
    case Event(Request.GetStatus, Data.Result(Failure(th))) => stop replying Status.Failure(th)
    case Event(Request.GetResult, Data.Result(Success(result))) => stop replying Response.Result(result.asInstanceOf[T])
    case Event(Request.GetResult, Data.Result(Failure(_))) => stay replying Response.NoResult
  }

  whenUnhandled {
    case Event(ReceiveTimeout, _) => stop()
  }

}

object TaskStatusActor {

  private[core] def props[T](id: UUID, name: String, waitForResultRequest: Boolean): Props = Props(new TaskStatusActor[T](id, name, waitForResultRequest))

  sealed trait State

  object State {

    object InProgress extends State

    object Finished extends State

  }

  sealed trait Data

  object Data {

    case object NoData extends Data

    case class Status(msg: String) extends Data

    case class Result[T](result: Try[T]) extends Data

  }

  sealed trait Request

  object Request {

    object GetStatus extends Request

    object GetResult extends Request

    case class PostResult[T](result: Try[T]) extends Request

    case class PostStatus(msg: String) extends Request

  }

  sealed trait Response

  object Response {

    case class Status(taskStatus: TaskStatus) extends Response

    object NoResult extends Response

    case class Result[T](result: T) extends Response

  }

}

sealed trait TaskStatusProcessor {

  def newStatus(msg: String): Unit

}

object TaskStatusProcessor {

  class ActorTaskStatusProcessor private[TaskStatusProcessor](val emptyTaskStatus: EmptyTaskStatus, actorRef: ActorRef) extends TaskStatusProcessor {

    def newStatus(msg: String) = actorRef ! TaskStatusActor.Request.PostStatus(msg)

    private[TaskStatusProcessor] def sendResult[T](result: Try[T]) = actorRef ! TaskStatusActor.Request.PostResult(result)

  }

  object EmptyTaskStatusProcessor extends TaskStatusProcessor {

    def newStatus(msg: String): Unit = {}

  }

  def create[T](taskId: UUID, taskName: String, waitForResultRequest: Boolean = true)(process: TaskStatusProcessor => Future[T])(implicit actorContext: ActorContext): Future[T] = {
    implicit val ec: ExecutionContext = actorContext.dispatcher
    val emptyTaskStatus = EmptyTaskStatus(taskId, taskName)
    val taskStatusProcessor = new ActorTaskStatusProcessor(emptyTaskStatus, actorContext.actorOf(TaskStatusActor.props(emptyTaskStatus.id, emptyTaskStatus.name, waitForResultRequest), emptyTaskStatus.id.toString))
    val taskProcess = process(taskStatusProcessor)
    taskProcess onComplete taskStatusProcessor.sendResult[T]
    taskProcess
  }

  def apply[T](taskName: String, waitForResultRequest: Boolean = true)(process: TaskStatusProcessor => T)(implicit actorContext: ActorContext): EmptyTaskStatus = {
    implicit val ec: ExecutionContext = actorContext.dispatcher
    val emptyTaskStatus = EmptyTaskStatus(UUID.randomUUID(), taskName)
    create(emptyTaskStatus.id, emptyTaskStatus.name, waitForResultRequest) { tsp =>
      Future {
        process(tsp)
      }
    }
    emptyTaskStatus
  }

}

class TaskStatusProvider private(actorRef: ActorRef)(implicit executionContext: ExecutionContext) {

  def status(implicit timeout: Timeout) = (actorRef ? TaskStatusActor.Request.GetStatus).collect {
    case TaskStatusActor.Response.Status(x) => x
  }

}

object TaskStatusProvider {

  def apply(taskId: UUID)(implicit actorContext: ActorContext) = actorContext.child(taskId.toString).map(actorRef => new TaskStatusProvider(actorRef)(actorContext.dispatcher))

  def status(taskId: UUID)(pf: PartialFunction[Try[TaskStatus], spray.routing.Route])(implicit actorContext: ActorContext, timeout: Timeout) = {
    import Directives.{onComplete, reject}
    implicit val ec = actorContext.dispatcher
    actorContext.child(taskId.toString).map(actorRef => new TaskStatusProvider(actorRef).status) match {
      case Some(fts) => onComplete(fts)(pf.orElse { case _ => reject })
      case None => reject
    }
  }

}

class TaskResultProvider private(actorRef: ActorRef)(implicit executionContext: ExecutionContext) {

  def result(implicit timeout: Timeout) = (actorRef ? TaskStatusActor.Request.GetResult).collect {
    case TaskStatusActor.Response.Result(result) => result
  }

}

object TaskResultProvider {

  def apply(taskId: UUID)(implicit actorContext: ActorContext) = actorContext.child(taskId.toString).map(actorRef => new TaskResultProvider(actorRef)(actorContext.dispatcher))

  def result(taskId: UUID)(pf: PartialFunction[Any, spray.routing.Route])(implicit actorContext: ActorContext, timeout: Timeout) = {
    import Directives.{onComplete, reject}
    implicit val ec = actorContext.dispatcher
    actorContext.child(taskId.toString).map(actorRef => new TaskResultProvider(actorRef).result) match {
      case Some(fr) => onComplete(fr) {
        case Success(result) => pf.applyOrElse[Any, spray.routing.Route](result, _ => reject)
        case _ => reject
      }
      case None => reject
    }
  }

}

sealed trait JsonTaskStatusWriter[T <: TaskStatus] extends RootJsonWriter[T] {

  def additionalProps(obj: T): Map[String, JsValue]

  def write(obj: T): JsValue = {
    val basicProps: Map[String, JsValue] = Map("taskId" -> JsString(obj.id.toString), "taskName" -> JsString(obj.name))
    JsObject(basicProps ++ additionalProps(obj))
  }

}

class JsonResultTaskStatusWriter[T](val resultLocation: Uri) extends JsonTaskStatusWriter[ResultTaskStatus[T]] {

  def additionalProps(obj: ResultTaskStatus[T]): Map[String, JsValue] = Map(
    "statusMessage" -> JsString("The task has been completed successfully."),
    "resultLocation" -> JsString(resultLocation.toString())
  )

}

class JsonEmptyTaskStatusWriter(val statusLocation: Uri) extends JsonTaskStatusWriter[EmptyTaskStatus] {

  def additionalProps(obj: EmptyTaskStatus): Map[String, JsValue] = Map("statusLocation" -> JsString(statusLocation.toString()))

}

class JsonMessageTaskStatusWriter(val statusLocation: Uri) extends JsonTaskStatusWriter[MessageTaskStatus] {

  def additionalProps(obj: MessageTaskStatus): Map[String, JsValue] = Map("statusMessage" -> JsString(obj.message), "statusLocation" -> JsString(statusLocation.toString()))

}

class JsonEmptyOrMessageTaskStatusWriter(val statusLocation: Uri) extends JsonTaskStatusWriter[TaskStatus] {

  def additionalProps(obj: TaskStatus): Map[String, JsValue] = obj match {
    case taskStatus: EmptyTaskStatus => new JsonEmptyTaskStatusWriter(statusLocation).additionalProps(taskStatus)
    case taskStatus: MessageTaskStatus => new JsonMessageTaskStatusWriter(statusLocation).additionalProps(taskStatus)
    case _ => throw JsonSerializationNotSupported
  }

}

trait TaskStatusRestHelper extends Directives with SprayJsonSupport with DefaultJsonProtocol {

  val baseUriPath: Uri.Path

  def statusUri(taskId: UUID)(implicit currentUri: Uri) = currentUri.withPath(baseUriPath / "task-status" / taskId.toString)

  def resultUri(taskId: UUID)(implicit currentUri: Uri) = currentUri.withPath(baseUriPath / "task-result" / taskId.toString)

  def completeResultTaskStatus(resultTaskStatus: ResultTaskStatus[Any])(implicit currentUri: Uri) = {
    val rurl = resultUri(resultTaskStatus.id)
    complete(
      StatusCodes.Created,
      List(HttpHeaders.Location(rurl)),
      resultTaskStatus.toJson(new JsonResultTaskStatusWriter(rurl)).asJsObject
    )
  }

  def completeAcceptedTaskStatus(emptyTaskStatus: EmptyTaskStatus)(implicit currentUri: Uri) = {
    val rurl = statusUri(emptyTaskStatus.id)
    complete(StatusCodes.Accepted, List(HttpHeaders.Location(rurl)), emptyTaskStatus.toJson(new JsonEmptyTaskStatusWriter(rurl)).asJsObject)
  }

  def taskStatusRoute(pf: PartialFunction[Try[TaskStatus], spray.routing.Route] = PartialFunction.empty)(implicit actorContext: ActorContext, timeout: Timeout = 5 seconds) = pathPrefix("task-status") {
    path(JavaUUID) { taskId =>
      requestUri { implicit uri =>
        val basicStatusRoute: PartialFunction[Try[TaskStatus], spray.routing.Route] = {
          case Success(EmptyOrMessageTaskStatus(taskStatus)) => complete(taskStatus.toJson(new JsonEmptyOrMessageTaskStatusWriter(uri)).asJsObject)
          case Success(taskStatus @ ResultTaskStatus(_, _, _: Any)) => completeResultTaskStatus(taskStatus)
          case Failure(ex) => throw ex
        }
        TaskStatusProvider.status(taskId)(basicStatusRoute.orElse(pf))
      }
    }
  }

  def taskResultRoute(pf: PartialFunction[Any, spray.routing.Route] = PartialFunction.empty)(implicit actorContext: ActorContext, timeout: Timeout = 5 seconds) = pathPrefix("task-result") {
    path(JavaUUID) { taskId =>
      TaskResultProvider.result(taskId)(pf)
    }
  }

}