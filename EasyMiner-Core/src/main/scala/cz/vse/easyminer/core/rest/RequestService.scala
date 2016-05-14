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
 * Created by propan on 15. 8. 2015.
 */
class RequestService extends Actor with Directives {

  implicit val ec = context.dispatcher

  context.setReceiveTimeout(1 minute)

  sendReceive

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

  def userAuthenticator(apiKey: String): ContextAuthenticator[User] = rc => {
    val p = Promise[Authentication[User]]()
    UserEndpoint(apiKey).getUser.onComplete {
      case Success(user) => p.success(Right(user))
      case Failure(ex) => p.success(Left(AuthenticationFailedRejection(AuthenticationFailedRejection.CredentialsRejected, rc.request.headers)))
    }
    p.future
  }

  def authenticateUser(apiKey: String)(rc: RequestContext): Unit = context.parent ! ParentRequest.ApiKey(apiKey, rc)

  def safeUser(apiKey: String)(user: User)(rc: RequestContext): Unit = context.parent ! ParentRequest.AuthenticatedUser(user, apiKey, rc)

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

  object ParentRequest {

    case class AuthenticatedUser(user: User, apiKey: String, rc: RequestContext) extends ParentRequest

    case class ApiKey(apiKey: String, rc: RequestContext) extends ParentRequest

  }

  sealed trait ParentResponse {
    val rc: RequestContext
  }

  object ParentResponse {

    case class NoUserActor(apiKey: String, rc: RequestContext) extends ParentResponse

    case class UserActor(actor: ActorRef, rc: RequestContext) extends ParentResponse

  }

}