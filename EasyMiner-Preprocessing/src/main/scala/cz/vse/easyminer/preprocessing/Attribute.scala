package cz.vse.easyminer.preprocessing

/**
 * Created by propan on 18. 12. 2015.
 */
sealed trait Attribute {
  val name: String
  val field: Int
}

case class SimpleAttribute(name: String, field: Int) extends Attribute

case class AttributeDetail(id: Int, name: String, field: Int, dataset: Int, `type`: AttributeType, uniqueValuesSize: Int, active: Boolean)

case class AttributeNumericDetail(id: Int, min: Double, max: Double, avg: Double)

sealed trait AttributeType

object NominalAttributeType extends AttributeType

object NumericAttributeType extends AttributeType
