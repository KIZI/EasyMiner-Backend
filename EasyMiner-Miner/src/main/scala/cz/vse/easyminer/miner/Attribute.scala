package cz.vse.easyminer.miner

import cz.vse.easyminer.data
import cz.vse.easyminer.preprocessing.ValueMapperOps.NormalizedValueMapper
import cz.vse.easyminer.preprocessing.{NormalizedValue, AttributeDetail}

sealed trait Attribute

object * extends Attribute

case class AllValues(attributeDetail: AttributeDetail) extends Attribute

case class FixedValue(attributeDetail: AttributeDetail, normalizedValue: NormalizedValue) extends Attribute

class MappedFixedValue(normalizedValueMapper: NormalizedValueMapper) {

  def unapply(expr: BoolExpression[Attribute]): Option[(AttributeDetail, data.Value)] = expr match {
    case Value(FixedValue(attribute, value)) => normalizedValueMapper.value(attribute, value).map(attribute -> _)
    case _ => None
  }

}

object MappedFixedValue {

  import scala.language.implicitConversions

  implicit def normalizedValueMapperToMappedFixedValue(normalizedValueMapper: NormalizedValueMapper): MappedFixedValue = new MappedFixedValue(normalizedValueMapper)

}