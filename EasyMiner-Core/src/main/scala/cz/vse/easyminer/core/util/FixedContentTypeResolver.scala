package cz.vse.easyminer.core.util

import spray.http.{ContentType, HttpCharsets}
import spray.routing.directives.ContentTypeResolver

/**
 * Created by propan on 19. 8. 2015.
 */
trait FixedContentTypeResolver {

  implicit val ctr = new ContentTypeResolver {
    def apply(fileName: String): ContentType = {
      val ct = ContentTypeResolver.Default(fileName)
      ct.mediaType.subType match {
        case "json" => ct.withCharset(HttpCharsets.`UTF-8`)
        case _ => ct
      }
    }
  }

}
