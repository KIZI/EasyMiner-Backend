package cz.vse.easyminer.miner.rest


import java.util.UUID

import akka.actor.ActorContext
import cz.vse.easyminer.core.util.AnyToInt
import spray.client.pipelining.{addHeader, _}
import spray.http._
import spray.routing.{Directives, Route, ValidationRejection}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by Vaclav Zeman on 5. 4. 2018.
  */
class ExtensionsService(apiKey: String)(implicit actorContext: ActorContext, ec: ExecutionContext) extends Directives {

  private def registeredHosts = Set("localhost", "easyminer-demo.lmcloud.vse.cz", "br-dev.lmcloud.vse.cz")

  private val tasks = collection.mutable.Map.empty[String, Uri]

  private lazy val pipeline = {
    val sr = sendReceive
    addHeader("Authorization", "ApiKey " + apiKey) ~> sr
  }

  private def sendTask(name: String, address: Uri, params: Map[String, String], body: Option[HttpEntity]): Future[HttpResponse] = {
    val taskId = UUID.randomUUID()
    val requestUri = address.withPath(address.path / taskId.toString)
    val bodyParts = (params + ("timeout" -> params.get("timeout").collect { case AnyToInt(x) => x }.getOrElse(10).toString)).mapValues(BodyPart(_))
    val bodyPartsWithBody = body.map(entity => bodyParts + ("body" -> BodyPart(entity))).getOrElse(bodyParts)
    pipeline.apply(Post(requestUri, MultipartFormData(bodyPartsWithBody))).map { response =>
      if (response.status == StatusCodes.Accepted) {
        tasks.synchronized {
          tasks += (taskId.toString -> requestUri)
        }
        HttpResponse(StatusCodes.Accepted, HttpEntity(taskId.toString))
      } else {
        HttpResponse(StatusCodes.BadRequest, response.entity)
      }
    }
  }

  private def getTaskResult(taskId: UUID, requestUri: Uri): Future[HttpResponse] = {
    pipeline.apply(Get(requestUri)).map { response =>
      response.status match {
        case StatusCodes.OK =>
          tasks.synchronized(tasks -= taskId.toString)
          HttpResponse(StatusCodes.OK, response.entity)
        case StatusCodes.Accepted => HttpResponse(StatusCodes.Accepted, response.entity)
        case StatusCodes.NotFound =>
          tasks.synchronized(tasks -= taskId.toString)
          HttpResponse(StatusCodes.NotFound)
        case _ => HttpResponse(StatusCodes.InternalServerError, response.entity)
      }
    }
  }

  val route: Route = pathPrefix("remote-task") {
    pathEnd {
      post {
        entity(as[MultipartFormData]) { data =>
          val params = data.fields.flatMap(x => x.name.map(_ -> x.entity)).filter(_._1 != "body").map(x => x._1 -> x._2.asString).toMap
          val body = data.get("body").map(_.entity)
          (params.get("name"), params.get("address").map(Uri(_))) match {
            case (Some(name), Some(address)) if registeredHosts(address.authority.host.address) =>
              val taskId = sendTask(name, address, params.filterKeys(x => x != "address"), body)
              complete(taskId)
            case _ => reject(ValidationRejection("Name or valid (registered) address parameter is missing."))
          }
        }
      }
    } ~ path(JavaUUID) { taskId =>
      get {
        tasks.synchronized(tasks.get(taskId.toString)) match {
          case Some(uri) =>
            val taskResult = getTaskResult(taskId, uri)
            complete(taskResult)
          case None => reject()
        }
      }
    }
  }

}