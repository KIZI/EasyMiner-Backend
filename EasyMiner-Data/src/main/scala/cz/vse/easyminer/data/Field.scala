package cz.vse.easyminer.data

/**
 * Created by propan on 3. 8. 2015.
 */
case class Field(name: String, `type`: FieldType)

case class FieldDetail(id: Int, dataSource: Int, name: String, `type`: FieldType, uniqueValuesSize: Int)

case class FieldNumericDetail(id: Int, min: Double, max: Double, avg: Double)

/**
 * Field data types
 */
sealed trait FieldType

object NominalFieldType extends FieldType

object NumericFieldType extends FieldType
