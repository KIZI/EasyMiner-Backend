/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.core.rest

import spray.http.StatusCode
import spray.routing._

/**
  * This is a service directive
  * If you use handleDafault wrapper for any service operation, then the body function will have user defined exception and rejection handler
  */
trait DefaulHandlers extends Directives {
  implicit val exceptionHandler: ExceptionHandler
  implicit val rejectionHandler: RejectionHandler

  def handleDefault(body: RequestContext => Unit) = handleExceptions(exceptionHandler) {
    handleRejections(rejectionHandler)(body)
  }
}

/**
  * Custom rejection with status code and message
  *
  * @param code    status code
  * @param message message
  */
case class CodeMessageRejection(code: StatusCode, message: String) extends Rejection

/**
  * Custom rejection with status code
  *
  * @param code status code
  */
case class CodeRejection(code: StatusCode) extends Rejection