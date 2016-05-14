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
 * Created by propan on 15. 8. 2015.
 */
class UserService private(user: User, val apiKey: String)
  extends HttpServiceActor
  with DefaultResponseHandlers
  with XmlErrorMessage
  with UserEndpoint
  with DbService {

  implicit val ec: ExecutionContext = context.dispatcher
  implicit val timeout = Timeout(10 seconds)

  context.setReceiveTimeout(5 minutes)

  val logger = LoggerFactory.getLogger("cz.vse.easyminer.miner.rest.UserService")

  logger.debug(s"User service ${self.path.toString} has been created.")

  val minerService = dbConnectors.map { implicit dbConnectors =>
    new MinerService()
  }

  val route = sealRoute {
    onSuccess(minerService)(_.route)
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

  def buildDbConnectors(mysqlUserDatabase: MysqlUserDatabase, hiveUserDatabase: HiveUserDatabase): DBConnectors = new PreprocessingDBConnectors(mysqlUserDatabase, hiveUserDatabase)

}

object UserService {

  def props(user: User, apiKey: String) = Props(new UserService(user, apiKey))

}