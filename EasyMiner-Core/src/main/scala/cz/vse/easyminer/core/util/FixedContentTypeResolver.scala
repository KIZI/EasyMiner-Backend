/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.core.util

import spray.http.{ContentType, HttpCharsets}
import spray.routing.directives.ContentTypeResolver

/**
  * Created by Vaclav Zeman on 19. 8. 2015.
  */

/**
  * This trait is for fixing of the ContentType header within HTTP response
  */
trait FixedContentTypeResolver {

  /**
    * This implicit ContentType resolver adds to json ContentType UTF-8 charset
    */
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
