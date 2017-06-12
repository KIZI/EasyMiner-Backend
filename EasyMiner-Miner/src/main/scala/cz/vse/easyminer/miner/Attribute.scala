/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.miner

import cz.vse.easyminer.data
import cz.vse.easyminer.preprocessing.AttributeDetail
import cz.vse.easyminer.preprocessing.ValueMapperOps.ItemMapper

/**
  * Atom of association rule
  */
sealed trait Attribute

/**
  * Any (or empty) atom of a side of an association rule
  */
object * extends Attribute

/**
  * Atom of association rule which contains all possible values of a particular attribute detail
  *
  * @param attributeDetail attribute detail
  */
case class AllValues(attributeDetail: AttributeDetail) extends Attribute

/**
  * Atom of association rule which represents couple attribute-value item
  *
  * @param attributeDetail attribute detail
  * @param item            value id
  */
case class FixedValue(attributeDetail: AttributeDetail, item: Int) extends Attribute

class MappedFixedValue(normalizedValueMapper: ItemMapper) {

  def unapply(expr: BoolExpression[Attribute]): Option[(AttributeDetail, data.NominalValue)] = expr match {
    case Value(FixedValue(attributeDetail, item)) => normalizedValueMapper.value(attributeDetail, item).map(attributeDetail -> _)
    case _ => None
  }

}

object MappedFixedValue {

  import scala.language.implicitConversions

  implicit def normalizedValueMapperToMappedFixedValue(normalizedValueMapper: ItemMapper): MappedFixedValue = new MappedFixedValue(normalizedValueMapper)

}