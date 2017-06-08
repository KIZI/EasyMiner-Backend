/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.core.rest

import akka.actor.{Actor, ActorRef, ReceiveTimeout}
import cz.vse.easyminer.core.User
import cz.vse.easyminer.core.rest.RequestService.{ParentRequest, ParentResponse}
import spray.client.pipelining._
import spray.http.HttpHeader
import spray.routing.authentication._
import spray.routing.{AuthenticationFailedRejection, Directives, RequestContext}

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
  * Created by Vaclav Zeman on 15. 8. 2015.
  */

/**
  * Authentication actor
  */
class RequestService extends Actor with Directives {

  implicit val ec = context.dispatcher

  context.setReceiveTimeout(1 minute)

  sendReceive

  /**
    * This is api key authenticator.
    * It processes api keys (in http header or query string).
    * If the key is not parsable then stop this actor and return failed rejection
    */
  val apiKeyAuthenticator: ContextAuthenticator[String] = rc => Future {
    val ApiKeyRegEx = """(?i)\s*apikey\s*(\w+)""".r
    val apiKey = rc.request.uri.query.get("apiKey").orElse {
      rc.request.headers.collectFirst {
        case HttpHeader("authorization", ApiKeyRegEx(apiKey)) => apiKey
      }
    }
    apiKey match {
      case Some(apiKey) => Right(apiKey)
      case None =>
        context stop self
        Left(AuthenticationFailedRejection(AuthenticationFailedRejection.CredentialsMissing, rc.request.headers))
    }
  }

  /**
    * This function return function which creates user actor by api key and return user information
    *
    * @param apiKey user api key
    * @return authenticator
    */
  def userAuthenticator(apiKey: String): ContextAuthenticator[User] = rc => {
    val p = Promise[Authentication[User]]()
    UserEndpoint(apiKey).getUser.onComplete {
      case Success(user) => p.success(Right(user))
      case Failure(ex) => p.success(Left(AuthenticationFailedRejection(AuthenticationFailedRejection.CredentialsRejected, rc.request.headers)))
    }
    p.future
  }

  /**
    * This function delegates api key request to main service
    *
    * @param apiKey api key
    * @param rc     request context
    */
  def authenticateUser(apiKey: String)(rc: RequestContext): Unit = context.parent ! ParentRequest.ApiKey(apiKey, rc)

  /**
    * This function delegates authenticated request to main service
    *
    * @param apiKey api key
    * @param user   user information
    * @param rc     request context
    */
  def safeUser(apiKey: String)(user: User)(rc: RequestContext): Unit = context.parent ! ParentRequest.AuthenticatedUser(user, apiKey, rc)

  /**
    * It handles authentication messages. There are several messages handlers:
    * 1. HTTP RequestContext - parse api key (use api key authenticator) and send it to main service
    * 2. No user actor - main service does not have any user actor for this api key, so try to create user endpoint instance (no actor) and send infomation to the main service
    * 3. User actor - send RequestContext to the user actor and stop this authentication actor
    * 4. Timeout - stop this actor
    *
    * @return Receive
    */
  def receive: Receive = {
    case rc: RequestContext => authenticate(apiKeyAuthenticator)(authenticateUser)(rc)
    case ParentResponse.NoUserActor(apiKey, rc) => authenticate(userAuthenticator(apiKey))(safeUser(apiKey))(rc)
    case ParentResponse.UserActor(actor, rc) =>
      actor ! rc
      context stop self
    case ReceiveTimeout => context stop self
  }

}

object RequestService {

  sealed trait ParentRequest {
    val rc: RequestContext
  }

  /**
    * Desired requests for RequestService
    */
  object ParentRequest {

    case class AuthenticatedUser(user: User, apiKey: String, rc: RequestContext) extends ParentRequest

    case class ApiKey(apiKey: String, rc: RequestContext) extends ParentRequest

  }

  sealed trait ParentResponse {
    val rc: RequestContext
  }

  /**
    * Supposed responses from RequestService
    */
  object ParentResponse {

    case class NoUserActor(apiKey: String, rc: RequestContext) extends ParentResponse

    case class UserActor(actor: ActorRef, rc: RequestContext) extends ParentResponse

  }

}