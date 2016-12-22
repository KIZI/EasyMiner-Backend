package cz.vse.easyminer.core.rest

import spray.http.HttpHeaders.{`Access-Control-Allow-Headers`, `Access-Control-Allow-Methods`, `Access-Control-Allow-Origin`}
import spray.http.{AllOrigins, HttpHeader, HttpMethods}
import spray.routing
import spray.routing.Directives

/**
  * Created by propan on 28. 5. 2016.
  */
trait GlobalRoute extends Directives {

  private val corsHeaders: List[HttpHeader] = List(`Access-Control-Allow-Origin`(AllOrigins),
    `Access-Control-Allow-Methods`(HttpMethods.GET, HttpMethods.PUT, HttpMethods.POST, HttpMethods.OPTIONS, HttpMethods.DELETE),
    `Access-Control-Allow-Headers`("Origin, X-Requested-With, Content-Type, Accept, Accept-Encoding, Accept-Language, Host, Referer, User-Agent"))

  val globalRoute: routing.Directive0 = respondWithSingletonHeaders(corsHeaders)

}
