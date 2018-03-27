/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.core.rest

import spray.http.HttpCharsets._
import spray.http.MediaTypes._
import spray.http.{HttpData, ContentType, HttpEntity}
import spray.json.{JsString, JsNumber, JsObject}

/**
  * Created by Vaclav Zeman on 5. 8. 2015.
  */

/**
  * Error message which will be returned by web service
  */
trait ErrorMessage {

  /**
    * Error message with type of HttpEntity
    *
    * @param code response code
    * @param name name of error
    * @param msg  message of error
    * @return HttpEntity
    */
  def errorMessage(code: Int, name: String, msg: String): HttpEntity

}

/**
  * Error message in XML format
  */
trait XmlErrorMessage extends ErrorMessage {

  def errorMessage(code: Int, name: String, msg: String): HttpEntity = HttpEntity(
    ContentType(`application/xml`, `UTF-8`), HttpData(<error>
      <code>{ code }</code>
      <name>{ name }</name>
      <message>{ msg }</message>
    </error>.toString())
  )

}

/**
  * Error message in JSON format
  */
trait JsonErrorMessage extends ErrorMessage {

  def errorMessage(code: Int, name: String, msg: String): HttpEntity = HttpEntity(
    ContentType(`application/json`, `UTF-8`), HttpData(JsObject(
      "error" -> JsObject(
        "code" -> JsNumber(code),
        "name" -> JsString(name),
        "message" -> JsString(msg)
      )
    ).prettyPrint)
  )

}