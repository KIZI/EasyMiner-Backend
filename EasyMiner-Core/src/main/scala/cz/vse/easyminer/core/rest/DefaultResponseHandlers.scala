package cz.vse.easyminer.core.rest

import cz.vse.easyminer.core.StatusCodeException
import org.slf4j.LoggerFactory
import spray.routing.{ExceptionHandler, Rejection, RejectionHandler, Route}

trait DefaultResponseHandlers extends DefaulHandlers with ErrorMessage {

  private def writeError(statusCode: Int, ex: Throwable) = requestUri { uri =>
    LoggerFactory.getLogger(ex.getClass.getName).error(s"Error with URI $uri", ex)
    complete(statusCode, errorMessage(statusCode, ex.getClass.getName, ex.getMessage))
  }

  implicit val exceptionHandler: ExceptionHandler = ExceptionHandler {
    case ex: StatusCodeException => writeError(ex.statusCode.intValue, ex)
    case ex: Throwable => writeError(500, ex)
  }

  implicit val rejectionHandler: RejectionHandler = RejectionHandler {
    case rejections =>
      mapHttpResponse(x =>
        x.withEntity(
          errorMessage(x.status.intValue, x.status.reason, x.entity.asString)
        )
      ) {
        RejectionHandler.Default.orElse[List[Rejection], Route] {
          case CodeMessageRejection(code, message) :: _ => complete(code, message)
          case CodeRejection(code) :: _ => complete(code, code.defaultMessage)
        }(rejections)
      }
  }

}