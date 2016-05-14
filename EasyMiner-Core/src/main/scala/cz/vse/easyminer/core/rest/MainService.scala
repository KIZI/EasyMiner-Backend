package cz.vse.easyminer.core.rest

import akka.actor.{Actor, Props}
import cz.vse.easyminer.core.User
import shapeless.HNil
import spray.routing._

/**
 * Created by propan on 17. 8. 2015.
 */
trait MainService extends Actor with HttpService with DefaulHandlers with Directives {

  val strBasePath: String

  val route: Route

  lazy val apiPrefix: PathMatcher[HNil] = if (strBasePath.nonEmpty) strBasePath / "api" else "api"

  private lazy val basePath = if (strBasePath.nonEmpty) pathPrefix(strBasePath) else noop

  def createUserActor(user: User, apiKey: String): Props

  val actorHandler: PartialFunction[Any, Unit] = {
    case RequestService.ParentRequest.ApiKey(apiKey, rc) => context.child(apiKey) match {
      case Some(actor) => sender() ! RequestService.ParentResponse.UserActor(actor, rc)
      case None => sender() ! RequestService.ParentResponse.NoUserActor(apiKey, rc)
    }
    case RequestService.ParentRequest.AuthenticatedUser(user, apiKey, rc) => context.child(apiKey) match {
      case Some(actor) => sender() ! RequestService.ParentResponse.UserActor(actor, rc)
      case None => sender() ! RequestService.ParentResponse.UserActor(context.actorOf(createUserActor(user, apiKey), apiKey), rc)
    }
  }

  def receive: Receive = actorHandler orElse runRoute(basePath(route))

}
