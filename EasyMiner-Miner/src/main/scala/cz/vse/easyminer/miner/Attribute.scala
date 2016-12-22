package cz.vse.easyminer.miner

import cz.vse.easyminer.data
import cz.vse.easyminer.preprocessing.AttributeDetail
import cz.vse.easyminer.preprocessing.ValueMapperOps.ItemMapper

sealed trait Attribute

object * extends Attribute

case class AllValues(attributeDetail: AttributeDetail) extends Attribute

case class FixedValue(attributeDetail: AttributeDetail, item: Int) extends Attribute

class MappedFixedValue(normalizedValueMapper: ItemMapper) {

  def unapply(expr: BoolExpression[Attribute]): Option[(AttributeDetail, data.Value)] = expr match {
    case Value(FixedValue(attributeDetail, item)) => normalizedValueMapper.value(attributeDetail, item).map(attributeDetail -> _)
    case _ => None
  }

}

object MappedFixedValue {

  import scala.language.implicitConversions

  implicit def normalizedValueMapperToMappedFixedValue(normalizedValueMapper: ItemMapper): MappedFixedValue = new MappedFixedValue(normalizedValueMapper)

}