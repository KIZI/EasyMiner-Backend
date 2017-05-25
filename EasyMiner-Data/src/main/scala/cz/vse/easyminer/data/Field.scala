/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data

/**
  * Created by Vaclav Zeman on 3. 8. 2015.
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