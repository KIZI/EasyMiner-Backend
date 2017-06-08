/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.core.rest

import akka.actor.ActorRefFactory
import cz.vse.easyminer.core._
import cz.vse.easyminer.core.rest.UserEndpoint.Exceptions.UserInformationRequestException
import cz.vse.easyminer.core.util.Conf
import spray.client.pipelining._
import spray.http.{HttpHeaders, HttpRequest, MediaTypes}
import spray.httpx.SprayJsonSupport
import spray.json._

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

/**
  * Created by Vaclav Zeman on 18. 8. 2015.
  */

/**
  * This is the basic trait for a user actor.
  * This contains method for getting information about user databases and the user profile
  */
trait UserEndpoint {

  /**
    * All user actors are identified by their api keys
    */
  val apiKey: String

  implicit def actorRefFactory: ActorRefFactory

  implicit val ec: ExecutionContext

  import DefaultJsonProtocol._
  import SprayJsonSupport._

  /**
    * Json formatter for user information
    */
  implicit val jsonUserFormat: RootJsonFormat[User] = jsonFormat3(User)

  /**
    * Json formatter (reader) for limited database information
    */
  implicit object JsonLimitedDbFormat extends RootJsonFormat[MysqlUserDatabase] {
    def write(obj: MysqlUserDatabase): JsValue = throw JsonSerializationNotSupported

    def read(json: JsValue): MysqlUserDatabase = {
      val fields = List("server", "username", "password", "database")
      json.asJsObject.getFields(fields: _*) match {
        case Seq(JsString(server), JsString(username), JsString(password), JsString(database)) => MysqlUserDatabase(server, database, username, password)
        case _ => throw new JsonDeserializeException(fields)
      }
    }
  }

  /**
    * Json formatter (reader) for unlimited database information
    */
  implicit object JsonUnlimitedDbFormat extends RootJsonFormat[HiveUserDatabase] {
    def write(obj: HiveUserDatabase): JsValue = throw JsonSerializationNotSupported

    def read(json: JsValue): HiveUserDatabase = {
      val fields = List("server", "port", "username", "database")
      json.asJsObject.getFields(fields: _*) match {
        case Seq(JsString(server), JsNumber(port), JsString(username), JsString(database)) => HiveUserDatabase(server, port.intValue(), database, username)
        case _ => throw new JsonDeserializeException(fields)
      }
    }
  }

  /**
    * This is base of the request to the easyminer user service.
    * The request accepts only json and adds api key header
    */
  private lazy val pipeline = addHeader(HttpHeaders.Accept(MediaTypes.`application/json`)) ~> addHeader("Authorization", "ApiKey " + apiKey) ~> sendReceive

  private def safeRequest[T](request: HttpRequest)(implicit f: HttpRequest => Future[T]) = f(request).transform(x => x, th => new UserInformationRequestException(request, th))

  /**
    * Request for information about user
    *
    * @return future object with user information
    */
  def getUser = {
    implicit val extPipeline: HttpRequest => Future[User] = pipeline ~> unmarshal[User]
    safeRequest(Get(UserEndpoint.userHttpEndpoint + UserEndpoint.authPath))
  }

  /**
    * Request for information about user unlimited database information
    *
    * @return future object with database information
    */
  def getUnlimitedDb = {
    implicit val extPipeline: HttpRequest => Future[HiveUserDatabase] = pipeline ~> unmarshal[HiveUserDatabase]
    safeRequest(Get(UserEndpoint.userHttpEndpoint + UserEndpoint.unlimitedDbPath))
  }

  /**
    * Request for information about user limited database information
    *
    * @return future object with database information
    */
  def getLimitedDb = {
    implicit val extPipeline: HttpRequest => Future[MysqlUserDatabase] = pipeline ~> unmarshal[MysqlUserDatabase]
    safeRequest(Get(UserEndpoint.userHttpEndpoint + UserEndpoint.limitedDbPath))
  }

}

object UserEndpoint {

  def apply(_apiKey: String)(implicit _system: ActorRefFactory, _ec: ExecutionContext) = new UserEndpoint {
    val apiKey: String = _apiKey
    implicit val ec: ExecutionContext = _ec

    implicit def actorRefFactory: ActorRefFactory = _system
  }

  object Exceptions {

    class UserInformationRequestException(request: HttpRequest, cause: Throwable) extends Exception("Request for user information failed: " + request.uri.toString(), cause)

  }

  val userHttpEndpoint = Conf().get[String]("easyminer.user.http-endpoint").trim.stripSuffix("/")
  val authPath = Conf().get[String]("easyminer.user.auth-path").trim.stripSuffix("/")
  val limitedDbPath = Conf().get[String]("easyminer.user.limited-db-path").trim.stripSuffix("/")
  val unlimitedDbPath = Conf().get[String]("easyminer.user.unlimited-db-path").trim.stripSuffix("/")

}