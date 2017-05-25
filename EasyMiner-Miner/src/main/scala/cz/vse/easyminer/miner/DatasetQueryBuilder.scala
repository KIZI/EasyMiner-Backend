/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.miner

import scalikejdbc._

trait DatasetQueryBuilder {

  def toSqlConditions(exp: BoolExpression[Attribute]): SQLSyntax

}