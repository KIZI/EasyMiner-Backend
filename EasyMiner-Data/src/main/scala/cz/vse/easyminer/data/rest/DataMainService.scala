/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data.rest

import akka.actor.Props
import cz.vse.easyminer.core.User
import cz.vse.easyminer.core.rest.{DefaultResponseHandlers, JsonErrorMessage, MainService, RequestService}
import cz.vse.easyminer.core.util.{Conf, FixedContentTypeResolver}
import spray.http.ContentTypes._
import spray.http.Uri
import spray.routing.{HttpServiceActor, RequestContext}

import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * Created by Vaclav Zeman on 15. 8. 2015.
 */
class DataMainService extends HttpServiceActor with MainService with DefaultResponseHandlers with JsonErrorMessage with FixedContentTypeResolver {

  implicit val ec = context.dispatcher

  val strBasePath: String = DataMainService.strBasePath

  def createUserActor(user: User, apiKey: String): Props = Props(new UserService(user, apiKey))

  def secure(rc: RequestContext): Unit = context.actorOf(Props(new RequestService)) ! rc

  val route = pathPrefix("api") {
    pathPrefix("v1") {
      path("doc.json") {
        getFromResource("swagger.json", `application/json`)
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

object DataMainService {

  val strBasePath: String = Conf().get[String]("easyminer.data.rest.base-path")

  trait BaseUriPath {

    val baseUriPath = {
      val startPath = if (strBasePath.isEmpty) Uri.Path.Empty else Uri.Path.Empty / strBasePath
      startPath / "api" / "v1"
    }

  }

  object BaseUriPath extends BaseUriPath

}