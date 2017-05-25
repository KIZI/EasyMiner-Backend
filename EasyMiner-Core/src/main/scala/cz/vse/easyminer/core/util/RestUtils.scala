/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.core.util

import spray.http.Uri

object RestUtils {
  
  implicit class PathExtension(path: Uri.Path) {
    private def findClosestParent(path: Uri.Path) : Uri.Path = path match {
      case Uri.Path.Empty => path
      case Uri.Path.Slash(tail) => findClosestParent(tail)
      case Uri.Path.Segment(_, Uri.Path.Slash(tail)) => tail
      case Uri.Path.Segment(_, path @ Uri.Path.Empty) => path 
    }
    def parent = findClosestParent(path.reverse).reverse
  }
  
}