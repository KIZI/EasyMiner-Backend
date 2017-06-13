/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.miner.rest

import akka.actor.Props
import cz.vse.easyminer.core.dependencies.{MysqlDependencyChecker, UserServiceDependencyChecker}
import cz.vse.easyminer.core.rest._
import cz.vse.easyminer.core.util.{Conf, FixedContentTypeResolver}
import cz.vse.easyminer.core.{DependencyChecker, User}
import cz.vse.easyminer.miner.impl.RDependencyChecker
import spray.http.ContentTypes.`application/json`
import spray.http.MediaTypes._
import spray.http.Uri
import spray.routing.{HttpServiceActor, RequestContext}

import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * Created by Vaclav Zeman on 15. 8. 2015.
  */

/**
  * Main service class
  * Here are main routing rules for REST mining service
  * Others rules are nested within this rules but are placed in other classes
  */
class MinerMainService extends HttpServiceActor with MainService with DefaultResponseHandlers with XmlErrorMessage with FixedContentTypeResolver {

  implicit val ec = context.dispatcher

  val strBasePath: String = Conf().get[String]("easyminer.miner.rest.base-path")

  /**
    * This creates a user actor from api key
    *
    * @param user   user information
    * @param apiKey api key
    * @return actor
    */
  def createUserActor(user: User, apiKey: String): Props = UserService.props(user, apiKey)

  /**
    * This function delegates request context to actor which represents the security facade.
    * Security actor then sends authorization information back to this main actor.
    *
    * @param rc request context
    */
  def secure(rc: RequestContext): Unit = context.actorOf(Props(new RequestService)) ! rc

  /**
    * Main routing rules
    */
  val route = pathPrefix("api") {
    pathPrefix("v1") {
      path("doc.json") {
        getFromResource("swagger.json", `application/json`)
      } ~ path("status") {
        detach() {
          respondWithMediaType(`application/xml`) {
            complete {
              DependencyChecker(
                new UserServiceDependencyChecker,
                new MysqlDependencyChecker(
                  Some { settings =>
                    DependencyChecker(new RDependencyChecker()(settings.dBConnector))
                  }
                )
              ).check()
              <status>ok</status>
            }
          }
        }
      } ~ secure
    }
  } ~ path("stop") {
    complete {
      context.system.scheduler.scheduleOnce(3 seconds) {
        context.system.shutdown()
        context.system.awaitTermination()
      }
      "Stopping..."
    }
  } ~ getFromDirectory("webapp")

}

object MinerMainService {

  val strBasePath: String = Conf().get[String]("easyminer.miner.rest.base-path")

  trait BaseUriPath {

    val baseUriPath = {
      val startPath = if (strBasePath.isEmpty) Uri.Path.Empty else Uri.Path.Empty / strBasePath
      startPath / "api" / "v1"
    }

  }

  object BaseUriPath extends BaseUriPath

}