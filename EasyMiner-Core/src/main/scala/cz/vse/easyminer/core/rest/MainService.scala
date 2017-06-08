/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.core.rest

import akka.actor.{Actor, Props}
import cz.vse.easyminer.core.User
import shapeless.HNil
import spray.routing._

/**
  * Created by Vaclav Zeman on 17. 8. 2015.
  */

/**
  * This is a root service for any web service within easyminer backend.
  */
trait MainService extends Actor with HttpService with DefaulHandlers with Directives with GlobalRoute {

  val strBasePath: String

  val route: Route

  /**
    * The REST service is reachable with base path uri with "api" suffix
    */
  lazy val apiPrefix: PathMatcher[HNil] = if (strBasePath.nonEmpty) strBasePath / "api" else "api"

  private lazy val basePath = if (strBasePath.nonEmpty) pathPrefix(strBasePath) else noop

  def createUserActor(user: User, apiKey: String): Props

  /**
    * Base actor message handler.
    * This processes non-http requests from other actors.
    * It handles authentication by user parameters or api keys.
    * 1. Request is ApiKey only: find actor by api key, if it exits then send back the actor, otherwise send no user actor message
    * 2. Request is ApiKey with user information: find actor by api key, if it exits then send back the actor, otherwise create user actor and send it
    */
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

  /**
    * First process non-http actor messages
    * Or else run the web service (add base path and global headers)
    *
    * @return route
    */
  def receive: Receive = actorHandler orElse runRoute(basePath(globalRoute(options(complete("")) ~ route)))

}
