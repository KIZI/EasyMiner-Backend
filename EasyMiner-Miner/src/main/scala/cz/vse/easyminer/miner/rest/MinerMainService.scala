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
  * Created by propan on 15. 8. 2015.
  */
class MinerMainService extends HttpServiceActor with MainService with DefaultResponseHandlers with XmlErrorMessage with FixedContentTypeResolver {

  implicit val ec = context.dispatcher

  val strBasePath: String = Conf().get[String]("easyminer.miner.rest.base-path")

  def createUserActor(user: User, apiKey: String): Props = UserService.props(user, apiKey)

  def secure(rc: RequestContext): Unit = context.actorOf(Props(new RequestService)) ! rc

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