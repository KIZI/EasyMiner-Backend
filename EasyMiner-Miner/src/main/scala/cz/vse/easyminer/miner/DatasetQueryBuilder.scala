package cz.vse.easyminer.miner

import cz.vse.easyminer.preprocessing.AttributeDetail
import scalikejdbc._

trait DatasetQueryBuilder {

  def toSQLSelect(exp: BoolExpression[Attribute])(implicit attributeToColName: AttributeDetail => SQLSyntax): Traversable[SQLSyntax]

  def toSQLSelectMap(exp: BoolExpression[Attribute])(implicit attributeToColName: AttributeDetail => SQLSyntax): Map[AttributeDetail, SQLSyntax]

}