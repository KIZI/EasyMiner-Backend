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
trait ErrorMessage {

  def errorMessage(code: Int, name: String, msg: String): HttpEntity

}

trait XmlErrorMessage extends ErrorMessage {

  def errorMessage(code: Int, name: String, msg: String): HttpEntity = HttpEntity(
    ContentType(`application/xml`, `UTF-8`), HttpData(<error>
      <code>{ code }</code>
      <name>{ name }</name>
      <message>{ msg }</message>
    </error>.toString())
  )

}

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