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
  * This library is for monitoring of some parts of scripts.
  * There is an actor which is running independently on the main process, but the the main process has access to this actor and can send messages with status of the progress.
  * WS addon: For this function there is a wrapper for web services. It can run process asynchronously with attached monitoring object.
  * WS addon: The process sends messages and then final result. All messages are converted into json and sent back to a client if there is some request for status or result.
  * Created by Vaclav Zeman on 9. 2. 2016.
  */

/**
  * Status trait with some message. It has id and name by default.
  */
sealed trait TaskStatus {
  val id: UUID
  val name: String
}

/**
  * Status with empty message. Process is in progress but there are no information about status
  *
  * @param id   task id
  * @param name task name
  */
case class EmptyTaskStatus(id: UUID, name: String) extends TaskStatus

/**
  * Status with message. It returns the current state of the task with a specific message.
  *
  * @param id      task id
  * @param name    task name
  * @param message status message
  */
case class MessageTaskStatus(id: UUID, name: String, message: String) extends TaskStatus

/**
  * This is result of the task.
  *
  * @param id     task id
  * @param name   task name
  * @param result task result
  * @tparam T type of the result
  */
case class ResultTaskStatus[T](id: UUID, name: String, result: T) extends TaskStatus

/**
  * Extractor for empty and message task status
  */
object EmptyOrMessageTaskStatus {

  def unapply(taskStatus: TaskStatus): Option[TaskStatus] = taskStatus match {
    case _: EmptyTaskStatus | _: MessageTaskStatus => Some(taskStatus)
    case _ => None
  }

}

/**
  * Task status actor. This actor is for monitoring of some process.
  *
  * @param id                   task id
  * @param name                 task name
  * @param waitForResultRequest if task is ended and the client requests for a status, then return status and stop monitoring (false) or return status and wait for result request (true)
  * @tparam T type of result of the task
  */
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

/**
  * Task monitor trait. The process can send a new status to the monitoring object
  */
sealed trait TaskStatusProcessor {

  /**
    * send a new status message to the associated monitoring actor.
    *
    * @param msg message
    */
  def newStatus(msg: String): Unit

}

object TaskStatusProcessor {

  /**
    * Status object which has attached some monitoring actor. This allows to send messages to this actor
    *
    * @param emptyTaskStatus default empty status
    * @param actorRef        status actor
    */
  class ActorTaskStatusProcessor private[TaskStatusProcessor](val emptyTaskStatus: EmptyTaskStatus, actorRef: ActorRef) extends TaskStatusProcessor {

    def newStatus(msg: String) = actorRef ! TaskStatusActor.Request.PostStatus(msg)

    private[TaskStatusProcessor] def sendResult[T](result: Try[T]) = actorRef ! TaskStatusActor.Request.PostResult(result)

  }

  /**
    * Default status object. If some process requires status monitoring object, but we do not want to use any monitor, then we can attach this object which sends message to nowhere.
    */
  object EmptyTaskStatusProcessor extends TaskStatusProcessor {

    def newStatus(msg: String): Unit = {}

  }

  /**
    * For process function "process" creates a monitoring actor and then fire the process function with created status object processor.
    * The process function requires task status processor as input and must return future object, so it must be launched asynchronously (like a parallel task).
    * As soons as the process finishes, then it automatically sends result into the monitoring actor.
    *
    * @param taskId               task id
    * @param taskName             task name
    * @param waitForResultRequest waiting flag for monitoring actor
    * @param process              main process function of the asynchronous task
    * @param actorContext         actor system
    * @tparam T task reslt
    * @return future object for the task result
    */
  def create[T](taskId: UUID, taskName: String, waitForResultRequest: Boolean = true)(process: TaskStatusProcessor => Future[T])(implicit actorContext: ActorContext): Future[T] = {
    implicit val ec: ExecutionContext = actorContext.dispatcher
    val emptyTaskStatus = EmptyTaskStatus(taskId, taskName)
    val taskStatusProcessor = new ActorTaskStatusProcessor(emptyTaskStatus, actorContext.actorOf(TaskStatusActor.props(emptyTaskStatus.id, emptyTaskStatus.name, waitForResultRequest), emptyTaskStatus.id.toString))
    val taskProcess = process(taskStatusProcessor)
    taskProcess onComplete taskStatusProcessor.sendResult[T]
    taskProcess
  }

  /**
    * This is wrapper for the create function.
    * This automatically generates task ID, launches process function with a monitoring actor and returns an empty status with task ID and name.
    * Task is running asynchronously.
    *
    * @param taskName             task name
    * @param waitForResultRequest waiting flag for monitoring actor
    * @param process              main process function of the asynchronous task
    * @param actorContext         actor system
    * @tparam T task reslt
    * @return empty status for the task
    */
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

/**
  * This object can send status request to a monitoring actor and then returns status message
  *
  * @param actorRef         monitoring actor for a task
  * @param executionContext actor system (context - parent)
  */
class TaskStatusProvider private(actorRef: ActorRef)(implicit executionContext: ExecutionContext) {

  /**
    * Get status of the task
    *
    * @param timeout waiting time for response
    * @return future object with status message
    */
  def status(implicit timeout: Timeout) = (actorRef ? TaskStatusActor.Request.GetStatus).collect {
    case TaskStatusActor.Response.Status(x) => x
  }

}

/**
  * This object creates status requestors for a specific tasks.
  */
object TaskStatusProvider {

  /**
    * Get a monitoring actor for a task ID
    *
    * @param taskId       task id
    * @param actorContext actor context (parent of monitoring actor)
    * @return status provider for a task or None if does not exist
    */
  def apply(taskId: UUID)(implicit actorContext: ActorContext) = actorContext.child(taskId.toString).map(actorRef => new TaskStatusProvider(actorRef)(actorContext.dispatcher))

  /**
    * Status provider for web service.
    * This function returns task status as route
    *
    * @param taskId       task id
    * @param pf           this function converts task status into the route object
    * @param actorContext actor context (parent of monitoring actor)
    * @param timeout      response timeout
    * @return service route
    */
  def status(taskId: UUID)(pf: PartialFunction[Try[TaskStatus], spray.routing.Route])(implicit actorContext: ActorContext, timeout: Timeout) = {
    import Directives.{onComplete, reject}
    implicit val ec = actorContext.dispatcher
    actorContext.child(taskId.toString).map(actorRef => new TaskStatusProvider(actorRef).status) match {
      case Some(fts) => onComplete(fts)(pf.orElse { case _ => reject })
      case None => reject
    }
  }

}

/**
  * This object creates result requestors for a specific tasks.
  */
class TaskResultProvider private(actorRef: ActorRef)(implicit executionContext: ExecutionContext) {

  /**
    * Get result of the task
    *
    * @param timeout waiting time for response
    * @return future object with result
    */
  def result(implicit timeout: Timeout) = (actorRef ? TaskStatusActor.Request.GetResult).collect {
    case TaskStatusActor.Response.Result(result) => result
  }

}

object TaskResultProvider {

  /**
    * Get a result provider with an actor for a task ID
    *
    * @param taskId       task id
    * @param actorContext actor context (parent of monitoring actor)
    * @return result provider for a task or None if does not exist
    */
  def apply(taskId: UUID)(implicit actorContext: ActorContext) = actorContext.child(taskId.toString).map(actorRef => new TaskResultProvider(actorRef)(actorContext.dispatcher))

  /**
    * Result provider for web service.
    * This function returns task result as route
    *
    * @param taskId       task id
    * @param pf           this function converts task result into the route object
    * @param actorContext actor context (parent of monitoring actor)
    * @param timeout      response timeout
    * @return service route
    */
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

/**
  * This is abstract for converting task status into json
  *
  * @tparam T status type
  */
sealed trait JsonTaskStatusWriter[T <: TaskStatus] extends RootJsonWriter[T] {

  def additionalProps(obj: T): Map[String, JsValue]

  def write(obj: T): JsValue = {
    val basicProps: Map[String, JsValue] = Map("taskId" -> JsString(obj.id.toString), "taskName" -> JsString(obj.name))
    JsObject(basicProps ++ additionalProps(obj))
  }

}

/**
  * Convert task result location to json
  *
  * @param resultLocation result location uri
  * @tparam T status type
  */
class JsonResultTaskStatusWriter[T](val resultLocation: Uri) extends JsonTaskStatusWriter[ResultTaskStatus[T]] {

  def additionalProps(obj: ResultTaskStatus[T]): Map[String, JsValue] = Map(
    "statusMessage" -> JsString("The task has been completed successfully."),
    "resultLocation" -> JsString(resultLocation.toString())
  )

}

/**
  * Convert empty task status into json
  *
  * @param statusLocation status location uri
  */
class JsonEmptyTaskStatusWriter(val statusLocation: Uri) extends JsonTaskStatusWriter[EmptyTaskStatus] {

  def additionalProps(obj: EmptyTaskStatus): Map[String, JsValue] = Map("statusLocation" -> JsString(statusLocation.toString()))

}

/**
  * Convert message task status into json
  *
  * @param statusLocation status location uri
  */
class JsonMessageTaskStatusWriter(val statusLocation: Uri) extends JsonTaskStatusWriter[MessageTaskStatus] {

  def additionalProps(obj: MessageTaskStatus): Map[String, JsValue] = Map("statusMessage" -> JsString(obj.message), "statusLocation" -> JsString(statusLocation.toString()))

}

/**
  * Convert empty or message task status into json
  *
  * @param statusLocation status location uri
  */
class JsonEmptyOrMessageTaskStatusWriter(val statusLocation: Uri) extends JsonTaskStatusWriter[TaskStatus] {

  def additionalProps(obj: TaskStatus): Map[String, JsValue] = obj match {
    case taskStatus: EmptyTaskStatus => new JsonEmptyTaskStatusWriter(statusLocation).additionalProps(taskStatus)
    case taskStatus: MessageTaskStatus => new JsonMessageTaskStatusWriter(statusLocation).additionalProps(taskStatus)
    case _ => throw JsonSerializationNotSupported
  }

}

/**
  * This is abstraction for task status services.
  * This contains routing for:
  * 1. Request for a task status -> it returns json with task status, result location if task has been ended, exception if error during task, or reject if not found
  * 2. Request for a task result -> it returns task result, or reject if not found
  */
trait TaskStatusRestHelper extends Directives with SprayJsonSupport with DefaultJsonProtocol {

  val baseUriPath: Uri.Path

  def statusUri(taskId: UUID)(implicit currentUri: Uri) = currentUri.withPath(baseUriPath / "task-status" / taskId.toString)

  def resultUri(taskId: UUID)(implicit currentUri: Uri) = currentUri.withPath(baseUriPath / "task-result" / taskId.toString)

  /**
    * This converts result status into the response where are information about result location.
    *
    * @param resultTaskStatus result task status
    * @param currentUri       current uri for status
    * @return route
    */
  def completeResultTaskStatus(resultTaskStatus: ResultTaskStatus[Any])(implicit currentUri: Uri) = {
    val rurl = resultUri(resultTaskStatus.id)
    complete(
      StatusCodes.Created,
      List(HttpHeaders.Location(rurl)),
      resultTaskStatus.toJson(new JsonResultTaskStatusWriter(rurl)).asJsObject
    )
  }

  /**
    * This converts empty status into response where are information about status location.
    *
    * @param emptyTaskStatus empty task status
    * @param currentUri      current uri
    * @return route
    */
  def completeAcceptedTaskStatus(emptyTaskStatus: EmptyTaskStatus)(implicit currentUri: Uri) = {
    val rurl = statusUri(emptyTaskStatus.id)
    complete(StatusCodes.Accepted, List(HttpHeaders.Location(rurl)), emptyTaskStatus.toJson(new JsonEmptyTaskStatusWriter(rurl)).asJsObject)
  }

  /**
    * Request for a task status
    */
  def taskStatusRoute(pf: PartialFunction[Try[TaskStatus], spray.routing.Route] = PartialFunction.empty)(implicit actorContext: ActorContext, timeout: Timeout = 5 seconds) = pathPrefix("task-status") {
    path(JavaUUID) { taskId =>
      requestUri { implicit uri =>
        val basicStatusRoute: PartialFunction[Try[TaskStatus], spray.routing.Route] = {
          case Success(EmptyOrMessageTaskStatus(taskStatus)) => complete(taskStatus.toJson(new JsonEmptyOrMessageTaskStatusWriter(uri)).asJsObject)
          case Success(taskStatus@ResultTaskStatus(_, _, _: Any)) => completeResultTaskStatus(taskStatus)
          case Failure(ex) => throw ex
        }
        TaskStatusProvider.status(taskId)(basicStatusRoute.orElse(pf))
      }
    }
  }

  /**
    * Request for a task result
    */
  def taskResultRoute(pf: PartialFunction[Any, spray.routing.Route] = PartialFunction.empty)(implicit actorContext: ActorContext, timeout: Timeout = 5 seconds) = pathPrefix("task-result") {
    path(JavaUUID) { taskId =>
      TaskResultProvider.result(taskId)(pf)
    }
  }

}