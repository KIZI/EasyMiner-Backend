package cz.vse.easyminer.data.rest

import java.util.UUID

import akka.actor.{Props, ActorContext}
import akka.util.Timeout
import cz.vse.easyminer.core.UnexpectedActorResponse
import cz.vse.easyminer.core.rest.{CodeMessageRejection, CodeRejection}
import cz.vse.easyminer.data.CompressionType
import cz.vse.easyminer.data.impl.{PreviewUploadActor, JsonFormatters}
import cz.vse.easyminer.data.impl.parser.{LineParser, LazyByteBufferInputStream}
import spray.http.StatusCodes
import spray.httpx.SprayJsonSupport
import spray.httpx.marshalling.BasicMarshallers
import spray.httpx.unmarshalling.BasicUnmarshallers
import spray.json.{RootJsonFormat, DefaultJsonProtocol}
import spray.routing.Directives
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import akka.pattern.ask

import scala.util.{Failure, Success}

/**
  * Created by propan on 5. 9. 2015.
  */
class PreviewUploadService(implicit actorContext: ActorContext) extends Directives with SprayJsonSupport {

  val maxChunkSize = 150000

  implicit val ec = actorContext.dispatcher
  implicit val defaultDuration: FiniteDuration = 5 seconds
  implicit val defaultTimeout = Timeout(defaultDuration)

  case class UploadMediaType(maxLines: Int, compression: Option[CompressionType])

  object JsonUploadMediaType extends DefaultJsonProtocol {

    import JsonFormatters.JsonCompressionType._

    implicit val JsonUploadMediaTypeFormat: RootJsonFormat[UploadMediaType] = jsonFormat2(UploadMediaType)
  }

  import JsonUploadMediaType._
  import BasicUnmarshallers._
  import BasicMarshallers._

  lazy val route = pathPrefix("preview") {
    post {
      path("start") {
        entity(as[UploadMediaType]) {
          case UploadMediaType(maxLines, compression) =>
            val id = UUID.randomUUID.toString
            val inputStreamWriter = new LazyByteBufferInputStream(10 * 1000 * 1000, 30 seconds)
            val futureResult = Future {
              LineParser(maxLines)(compression).parse(inputStreamWriter)
            }
            actorContext.actorOf(Props(new PreviewUploadActor(id, inputStreamWriter, futureResult)), id)
            complete(id)
          case _ => reject(CodeRejection(StatusCodes.UnsupportedMediaType))
        }
      } ~ path(JavaUUID) { id =>
        entity(as[Option[Array[Byte]]]) { data =>
          if (data.map(_.length).getOrElse(0) > maxChunkSize) {
            reject(CodeMessageRejection(StatusCodes.RequestEntityTooLarge, s"Uploaded chunk is too large. Maximum is $maxChunkSize bytes."))
          } else {
            actorContext.child(id.toString) match {
              case Some(uploadActor) =>
                val result = data match {
                  case Some(data) if data.length > 0 => uploadActor ? PreviewUploadActor.Request.Data(data)
                  case _ => uploadActor ? PreviewUploadActor.Request.Finish
                }
                onComplete(result) {
                  case Success(PreviewUploadActor.Response.InProgress) => complete(StatusCodes.Accepted)
                  case Success(PreviewUploadActor.Response.Finished(data)) => complete(data)
                  case Failure(ex) => throw ex
                  case _ => throw UnexpectedActorResponse
                }
              case None => reject
            }
          }
        }
      }
    }
  }

}
