package cz.vse.easyminer.preprocessing.rest

import akka.actor._
import cz.vse.easyminer.core._
import cz.vse.easyminer.core.db.DBConnectors
import cz.vse.easyminer.core.rest.{DbService, DefaultResponseHandlers, JsonErrorMessage, UserEndpoint}
import cz.vse.easyminer.preprocessing.DatasetDetail
import cz.vse.easyminer.preprocessing.impl.TypeableCases._
import cz.vse.easyminer.preprocessing.impl.{JsonFormatters, PreprocessingDBConnectors}
import org.slf4j.LoggerFactory
import spray.httpx.SprayJsonSupport
import spray.json._
import spray.routing.{HttpServiceActor, RequestContext}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * Created by propan on 15. 8. 2015.
 */
class UserService(user: User, val apiKey: String)
  extends HttpServiceActor
  with DefaultResponseHandlers
  with JsonErrorMessage
  with DbService
  with UserEndpoint
  with SprayJsonSupport
  with DefaultJsonProtocol
  with TaskStatusRestHelper
  with PreprocessingMainService.BaseUriPath {

  import JsonFormatters.JsonAttributeDetail._
  import JsonFormatters.JsonDatasetDetail._
  import JsonFormatters.JsonValueInterval._

  implicit val ec: ExecutionContext = context.dispatcher

  context.setReceiveTimeout(5 minutes)

  val logger = LoggerFactory.getLogger("cz.vse.easyminer.preprocessing.rest.UserService")

  logger.debug(s"User service ${self.path.toString} has been created.")

  val datasetService = dbConnectors.map { implicit dbConnectors =>
    new DatasetService
  }

  val route = sealRoute {
    pathPrefix("dataset") {
      onSuccess(datasetService)(_.route)
    } ~ taskStatusRoute() ~ taskResultRoute {
      case datasetDetail: DatasetDetail => complete(datasetDetail.toJson.asJsObject)
      case `Seq[AttributeDetail]`(attributeDetails) => complete(attributeDetails.toJson.asInstanceOf[JsArray])
      case `Seq[ValueInterval]`(valueIntervals) => complete(valueIntervals.toJson.asInstanceOf[JsArray])
    }
  }

  def receive: Receive = {
    case rc: RequestContext => route(rc)
    case ReceiveTimeout => if (context.children.isEmpty) {
      context stop self
    }
  }

  override def postStop(): Unit = {
    logger.debug(s"User service ${self.path.toString} is stopping...")
    dbConnectors.foreach(_.close())
  }

  def buildDbConnectors(mysqlUserDatabase: MysqlUserDatabase, hiveUserDatabase: Option[HiveUserDatabase]): DBConnectors = new PreprocessingDBConnectors(mysqlUserDatabase, hiveUserDatabase)

}