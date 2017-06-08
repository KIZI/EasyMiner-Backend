/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.core.rest

import spray.http.HttpHeaders.{`Access-Control-Allow-Headers`, `Access-Control-Allow-Methods`, `Access-Control-Allow-Origin`}
import spray.http.{AllOrigins, HttpHeader, HttpMethods}
import spray.routing
import spray.routing.Directives

/**
  * Created by Vaclav Zeman on 28. 5. 2016.
  */

/**
  * This is global directive.
  * Any operation within web service should be wrapped by this route.
  * This adds access control headers for cross-site scripting
  */
trait GlobalRoute extends Directives {

  private val corsHeaders: List[HttpHeader] = List(`Access-Control-Allow-Origin`(AllOrigins),
    `Access-Control-Allow-Methods`(HttpMethods.GET, HttpMethods.PUT, HttpMethods.POST, HttpMethods.OPTIONS, HttpMethods.DELETE),
    `Access-Control-Allow-Headers`("Origin, X-Requested-With, Content-Type, Accept, Accept-Encoding, Accept-Language, Host, Referer, User-Agent"))

  val globalRoute: routing.Directive0 = respondWithSingletonHeaders(corsHeaders)

}
