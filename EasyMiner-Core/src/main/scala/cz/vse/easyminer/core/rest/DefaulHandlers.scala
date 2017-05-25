/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.core.rest

import spray.http.StatusCode
import spray.routing._

trait DefaulHandlers extends Directives {
  implicit val exceptionHandler: ExceptionHandler
  implicit val rejectionHandler: RejectionHandler

  def handleDefault(body: RequestContext => Unit) = handleExceptions(exceptionHandler) {
    handleRejections(rejectionHandler)(body)
  }
}

case class CodeMessageRejection(code: StatusCode, message: String) extends Rejection

case class CodeRejection(code: StatusCode) extends Rejection