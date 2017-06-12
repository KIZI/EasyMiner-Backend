/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.miner

import scalikejdbc._

/**
  * Create database queries from a rule definition
  */
trait DatasetQueryBuilder {

  /**
    * Create sql query syntax from a rule statement
    *
    * @param exp statement
    * @return sql syntax
    */
  def toSqlConditions(exp: BoolExpression[Attribute]): SQLSyntax

}