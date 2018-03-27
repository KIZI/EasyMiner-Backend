/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.miner.rest

import akka.actor._
import akka.util.Timeout
import cz.vse.easyminer.core._
import cz.vse.easyminer.core.db.DBConnectors
import cz.vse.easyminer.core.rest.{DbService, DefaultResponseHandlers, UserEndpoint, XmlErrorMessage}
import cz.vse.easyminer.preprocessing.impl.PreprocessingDBConnectors
import org.slf4j.LoggerFactory
import spray.routing.{HttpServiceActor, RequestContext}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * Created by Vaclav Zeman on 15. 8. 2015.
  */

/**
  * This is the main actor for request/response control for a specific user
  *
  * @param user   user information
  * @param apiKey api key
  */
class UserService private(user: User, val apiKey: String)
  extends HttpServiceActor
    with DefaultResponseHandlers
    with XmlErrorMessage
    with UserEndpoint
    with DbService {

  implicit val ec: ExecutionContext = context.dispatcher
  implicit val timeout = Timeout(10 seconds)

  /**
    * This actor lives 5 minutes after last request from the user.
    * Therefore we do not need to authenticate all requests by third part, but we preserve user states some time
    */
  context.setReceiveTimeout(5 minutes)

  val logger = LoggerFactory.getLogger("cz.vse.easyminer.miner.rest.UserService")

  logger.debug(s"User service ${self.path.toString} has been created.")

  /**
    * Create service for mining which requires database connections
    */
  val minerService = dbConnectors.map { implicit dbConnectors =>
    new MinerService()
  }

  val outlierDetectionService = dbConnectors.map { implicit dbConnectors =>
    new OutlierDetectionService()
  }

  val route = sealRoute {
    onSuccess(minerService)(_.route) ~ onSuccess(outlierDetectionService)(_.route)
  }

  def receive: Receive = {
    case rc: RequestContext => route(rc)
    case ReceiveTimeout => if (context.children.isEmpty) {
      context stop self
    }
  }

  /**
    * After stopping this actor close all database connection of this user
    */
  override def postStop(): Unit = {
    logger.debug(s"User service ${self.path.toString} is stopping...")
    dbConnectors.foreach(_.close())
  }

  /**
    * Function for creation of database connectors by user database settings
    *
    * @param mysqlUserDatabase mysql database settings
    * @param hiveUserDatabase  optinal hive database settings
    * @return database connectors
    */
  def buildDbConnectors(mysqlUserDatabase: MysqlUserDatabase, hiveUserDatabase: Option[HiveUserDatabase]): DBConnectors = new PreprocessingDBConnectors(mysqlUserDatabase, hiveUserDatabase)

}

object UserService {

  def props(user: User, apiKey: String) = Props(new UserService(user, apiKey))

}