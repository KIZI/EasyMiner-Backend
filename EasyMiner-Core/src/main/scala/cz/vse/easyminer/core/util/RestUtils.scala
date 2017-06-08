/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.core.util

import spray.http.Uri

/**
  * Utils for RESTful services
  */
object RestUtils {

  /**
    * Extension for Uri.Path object
    *
    * @param path path of URI
    */
  implicit class PathExtension(path: Uri.Path) {

    private def findClosestParent(path: Uri.Path): Uri.Path = path match {
      case Uri.Path.Empty => path
      case Uri.Path.Slash(tail) => findClosestParent(tail)
      case Uri.Path.Segment(_, Uri.Path.Slash(tail)) => tail
      case Uri.Path.Segment(_, path@Uri.Path.Empty) => path
    }

    /**
      * Get parent of this URI path
      *
      * @return path of the parent
      */
    def parent = findClosestParent(path.reverse).reverse
  }

}