/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data.rest

import java.io.InputStream
import java.util.UUID

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import cz.vse.easyminer.core._
import cz.vse.easyminer.core.db.DBConnectors
import cz.vse.easyminer.core.rest.{CodeMessageRejection, CodeRejection}
import cz.vse.easyminer.data._
import cz.vse.easyminer.data.impl.parser.{CsvInputParser, LazyByteBufferInputStream, RdfInputParser}
import cz.vse.easyminer.data.impl.{JsonFormatters, UploadActor}
import org.slf4j.LoggerFactory
import spray.http.StatusCodes
import spray.httpx.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, _}
import spray.routing.Directives

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
  * Created by Vaclav Zeman on 24. 7. 2015.
  */

/**
  * This handles requests for operations which creates a data source
  * Upload initialization, streaming upload, finish upload
  *
  * @param dBConnectors database connections
  * @param actorContext user actor context
  */
class UploadService(implicit dBConnectors: DBConnectors, actorContext: ActorContext) extends Directives with SprayJsonSupport {

  val maxChunkSize = 1500000
  implicit val ec = actorContext.dispatcher
  implicit val defaultDuration: FiniteDuration = 5 seconds
  implicit val defaultTimeout = Timeout(defaultDuration)

  val logger = LoggerFactory.getLogger("cz.vse.easyminer.data.rest.UploadService")

  case class UploadMediaType(name: String, mediaType: String, dbType: DataSourceType)

  object JsonUploadMediaType extends DefaultJsonProtocol {

    import JsonFormatters.JsonDataSourceType._

    implicit val JsonUploadMediaTypeFormat: RootJsonFormat[UploadMediaType] = jsonFormat3(UploadMediaType)
  }

  object JsonUploadSettings extends DefaultJsonProtocol {

    import JsonFormatters.JsonCharset._
    import JsonFormatters.JsonCompressionType._
    import JsonFormatters.JsonFieldType._
    import JsonFormatters.JsonLocale._
    import JsonFormatters.JsonRdfLang._

    implicit val JsonUploadCsvSettingsFormat: RootJsonFormat[CsvInputParser.Settings] = jsonFormat8(CsvInputParser.Settings)
    implicit val JsonUploadRdfSettingsFormat: RootJsonFormat[RdfInputParser.Settings] = jsonFormat2(RdfInputParser.Settings)

  }

  import JsonFormatters.JsonDataSourceDetail._
  import JsonUploadMediaType._
  import JsonUploadSettings._
  import cz.vse.easyminer.data.impl.db.DataSourceTypeConversions._

  private def taskIdFromUploadId(id: UUID) = UUID.nameUUIDFromBytes((id.toString + "status").getBytes)

  private def startUpload(dataSourceType: DataSourceType)(startDataReader: InputStream => TaskStatusProcessor => Future[DataSourceDetail]) = {
    val id = UUID.randomUUID
    val inputStreamWriter = new LazyByteBufferInputStream(10 * 1000 * 1000, 30 seconds)
    val futureResult = TaskStatusProcessor.create(taskIdFromUploadId(id), "Uploading process", false) { tsp =>
      startDataReader(inputStreamWriter)(tsp)
    }
    actorContext.actorOf(
      UploadActor.props(id.toString, inputStreamWriter, futureResult, dataSourceType.toDataSourceOps),
      id.toString
    )
    complete(id.toString)
  }

  private def startCsvUpload(name: String, dataSourceType: DataSourceType) = entity(as[CsvInputParser.Settings]) { csvSettings =>
    startUpload(dataSourceType) { inputStream => implicit tsp =>
      Future {
        val dataSourceBuilder = dataSourceType.toDataSourceBuilder(name)
        val handler = new CsvInputParser(dataSourceBuilder, csvSettings)
        handler.write(inputStream)
      }
    }
  }

  private def startRdfUpload(name: String, dataSourceType: DataSourceType) = entity(as[RdfInputParser.Settings]) { rdfSettings =>
    startUpload(dataSourceType) { inputStream => implicit tsp =>
      Future {
        val dataSourceBuilder = dataSourceType.toDataSourceBuilder(name)
        val handler = RdfInputParser(dataSourceBuilder, rdfSettings)
        handler.write(inputStream)
      }
    }
  }

  lazy val route = post {
    path("start") {
      entity(as[UploadMediaType]) {
        case UploadMediaType(name, "csv", dataSourceType) => startCsvUpload(name, dataSourceType)
        case UploadMediaType(name, "rdf", dataSourceType) => startRdfUpload(name, dataSourceType)
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
                case Some(data) if data.nonEmpty => uploadActor ? UploadActor.Request.Data(data)
                case _ => uploadActor ? UploadActor.Request.Finish
              }
              val taskStatus = TaskStatusProvider(taskIdFromUploadId(id)).map(_.status).getOrElse(Future.failed(UnexpectedActorResponse))
              onComplete(result zip taskStatus) {
                case Success((UploadActor.Response.InProgress, MessageTaskStatus(_, _, msg))) => complete(StatusCodes.Accepted, msg)
                case Success((UploadActor.Response.InProgress, _)) => complete(StatusCodes.Accepted)
                case Success((UploadActor.Response.SlowDown, _)) => complete(StatusCodes.TooManyRequests)
                case Success((UploadActor.Response.Finished(result), _)) => complete(result)
                case Failure(th) => throw th
                case _ => throw UnexpectedActorResponse
              }
            case None => reject
          }
        }
      }
    }
  }

}