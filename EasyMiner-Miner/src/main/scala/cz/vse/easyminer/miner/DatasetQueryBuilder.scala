package cz.vse.easyminer.miner

import scalikejdbc._

trait DatasetQueryBuilder {

  def toSqlConditions(exp: BoolExpression[Attribute]): SQLSyntax

}