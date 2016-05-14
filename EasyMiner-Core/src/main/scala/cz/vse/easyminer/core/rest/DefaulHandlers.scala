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