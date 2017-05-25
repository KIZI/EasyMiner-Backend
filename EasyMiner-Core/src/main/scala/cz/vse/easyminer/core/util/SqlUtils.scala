/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.core.util

import scalikejdbc._

/**
  * Created by Vaclav Zeman on 29. 6. 2016.
  */
trait SqlUtils {

  def stringifySqlSyntax(sqlSyntax: SQLSyntax) = {
    val params = sqlSyntax.parameters.toIterator
    sqlSyntax.value.foldLeft("") { (s, c) =>
      if (c == '?' && params.hasNext) {
        s + params.next()
      } else {
        s + c
      }
    }
  }

}

object SqlUtils extends SqlUtils