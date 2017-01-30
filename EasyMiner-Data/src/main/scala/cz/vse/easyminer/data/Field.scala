package cz.vse.easyminer.data

/**
  * Created by propan on 3. 8. 2015.
  */
case class Field(name: String, `type`: FieldType)

case class FieldDetail(id: Int, dataSource: Int, name: String, `type`: FieldType, uniqueValuesSizeNominal: Int, uniqueValuesSizeNumeric: Int, supportNominal: Int, supportNumeric: Int) {
  def uniqueValuesSize = if (`type` == NominalFieldType) uniqueValuesSizeNominal else uniqueValuesSizeNumeric

  def support = if (`type` == NominalFieldType) supportNominal else supportNumeric
}

case class FieldNumericDetail(id: Int, min: Double, max: Double, avg: Double)

/**
  * Field data types
  */
sealed trait FieldType

object NominalFieldType extends FieldType

object NumericFieldType extends FieldType