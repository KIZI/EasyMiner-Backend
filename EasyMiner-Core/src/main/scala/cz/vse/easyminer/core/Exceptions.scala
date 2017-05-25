/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.core

import spray.http.{StatusCodes, StatusCode}
import spray.json.{SerializationException, DeserializationException}

/**
 * Created by Vaclav Zeman on 5. 8. 2015.
 */
object UnexpectedActorRequest extends Exception("Unexpected actor request.")

object UnexpectedActorResponse extends Exception("Unexpected actor response.")

class ActorDoesNotExist(name: String) extends Exception(s"Actor '$name' does not exist.")

class JsonDeserializeException(attributes: List[String]) extends DeserializationException("Invalid JSON input data. The document should have these attributes: " + attributes.mkString(", "), fieldNames = attributes)

class JsonDeserializeAttributeException(attribute: String, shouldBe: String = "") extends DeserializationException(s"Invalid attribute '$attribute' within the JSON document. $shouldBe")

object JsonDeserializationNotSupported extends DeserializationException("Deserialization of this object is not supported.")

object JsonSerializationNotSupported extends SerializationException("Serialization of this object is not supported.")

object UnexpectedDBType extends Exception("This database type is not expected.")

sealed trait StatusCodeException extends Exception {
  val statusCode: StatusCode
}

object StatusCodeException {

  trait NotFound extends StatusCodeException {
    val statusCode: StatusCode = StatusCodes.NotFound
  }

  trait BadRequest extends StatusCodeException {
    val statusCode: StatusCode = StatusCodes.BadRequest
  }

  trait NotImplemented extends StatusCodeException {
    val statusCode: StatusCode = StatusCodes.NotImplemented
  }

  trait ServiceUnavailable extends StatusCodeException {
    val statusCode: StatusCode = StatusCodes.ServiceUnavailable
  }

  trait Locked extends StatusCodeException {
    val statusCode: StatusCode = StatusCodes.Locked
  }

}