/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data.rest

import akka.actor._
import cz.vse.easyminer.core._
import cz.vse.easyminer.core.db.DBConnectors
import cz.vse.easyminer.core.rest.{DbService, DefaultResponseHandlers, JsonErrorMessage, UserEndpoint}
import cz.vse.easyminer.data.impl.TypeableCases._
import cz.vse.easyminer.data.impl.{DataDBConnectors, JsonFormatters}
import org.slf4j.LoggerFactory
import spray.httpx.SprayJsonSupport
import spray.json._
import spray.routing.{HttpServiceActor, RequestContext}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * Created by Vaclav Zeman on 15. 8. 2015.
 */
class UserService(user: User, val apiKey: String)
  extends HttpServiceActor
  with DefaultResponseHandlers
  with JsonErrorMessage
  with SprayJsonSupport
  with DefaultJsonProtocol
  with TaskStatusRestHelper
  with DataMainService.BaseUriPath
  with DbService
  with UserEndpoint {

  import JsonFormatters.JsonValueInterval._

  implicit val ec: ExecutionContext = context.dispatcher

  context.setReceiveTimeout(5 minutes)

  val logger = LoggerFactory.getLogger("cz.vse.easyminer.data.rest.UserService")

  logger.debug(s"User service ${self.path.toString} has been created.")

  val dataSourceService = dbConnectors.map { implicit dbConnectors =>
    new DataSourceService
  }

  val previewUploadService = new PreviewUploadService

  val uploadService = dbConnectors.map { implicit dbConnectors =>
    new UploadService
  }

  val route = sealRoute {
    pathPrefix("upload") {
      previewUploadService.route ~ onSuccess(uploadService)(_.route)
    } ~ pathPrefix("datasource") {
      onSuccess(dataSourceService)(_.route)
    } ~ taskStatusRoute() ~ taskResultRoute {
      case `Seq[ValueInterval]`(valueIntervals) => complete(valueIntervals.toJson.asInstanceOf[JsArray])
    }
  }

  def receive: Receive = {
    case rc: RequestContext => route(rc)
    case ReceiveTimeout => if (context.children.isEmpty) {
      context stop self
    }
  }

  def buildDbConnectors(mysqlUserDatabase: MysqlUserDatabase, hiveUserDatabase: Option[HiveUserDatabase]): DBConnectors = new DataDBConnectors(mysqlUserDatabase, hiveUserDatabase)

  override def postStop(): Unit = {
    logger.debug(s"User service ${self.path.toString} is stopping...")
    dbConnectors.foreach(_.close())
  }

}