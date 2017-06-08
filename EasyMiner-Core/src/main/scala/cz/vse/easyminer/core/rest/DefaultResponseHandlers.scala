/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.core.rest

import cz.vse.easyminer.core.StatusCodeException
import org.slf4j.LoggerFactory
import spray.routing.{ExceptionHandler, Rejection, RejectionHandler, Route}

/**
  * Default exception and rejection handler for web service
  */
trait DefaultResponseHandlers extends DefaulHandlers with ErrorMessage with GlobalRoute {

  /**
    * Get route which returns error information
    *
    * @param statusCode status code
    * @param ex         exception
    * @return route
    */
  private def writeError(statusCode: Int, ex: Throwable) = requestUri { uri =>
    LoggerFactory.getLogger(ex.getClass.getName).error(s"Error with URI $uri", ex)
    globalRoute(complete(statusCode, errorMessage(statusCode, ex.getClass.getName, ex.getMessage)))
  }

  /**
    * This catches all exceptions for ws.
    * If the exception has statuscode, then return error response with this code
    * If no statuscode, then return 500
    */
  implicit val exceptionHandler: ExceptionHandler = ExceptionHandler {
    case ex: StatusCodeException => writeError(ex.statusCode.intValue, ex)
    case ex: Throwable => writeError(500, ex)
  }

  /**
    * Map rejections into error message
    */
  implicit val rejectionHandler: RejectionHandler = RejectionHandler {
    case rejections =>
      globalRoute {
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

}