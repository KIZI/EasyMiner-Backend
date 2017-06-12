/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data

/**
  * Created by Vaclav Zeman on 3. 8. 2015.
  */

/**
  * Simple field object which represents basic field information
  *
  * @param name   field name
  * @param `type` field type (nominal, numeric)
  */
case class Field(name: String, `type`: FieldType)

/**
  * Detail of a field which is saved in a database and attached to a data source.
  * If this field is nominal, then it can have more values than the numeric type; therefore there are stats for both numeric and nominal types.
  * If we change nominal to numeric, then for this field we ignore string values which can not be converted to number (support and uniquevalues will be less).
  * But we need to preserve original string values because we may change the field type back to nominal (without any loss of information).
  *
  * @param id                      field id
  * @param dataSource              data source id
  * @param name                    field name
  * @param `type`                  field type (nominal, numeric)
  * @param uniqueValuesSizeNominal number of distinct nominal values for this field
  * @param uniqueValuesSizeNumeric number of distinct numeric values for this field
  * @param supportNominal          number of transactions which contains this field
  * @param supportNumeric          number of transactions which contains this field (only numeric values)
  */
case class FieldDetail(id: Int, dataSource: Int, name: String, `type`: FieldType, uniqueValuesSizeNominal: Int, uniqueValuesSizeNumeric: Int, supportNominal: Int, supportNumeric: Int) {
  /**
    * number of distinct values for this field and type of this field
    *
    * @return number of distinct values
    */
  def uniqueValuesSize = if (`type` == NominalFieldType) uniqueValuesSizeNominal else uniqueValuesSizeNumeric

  /**
    * number of transactions which contains this field for the set type
    *
    * @return number of covering transactions
    */
  def support = if (`type` == NominalFieldType) supportNominal else supportNumeric
}

/**
  * Basic stats for numeric field
  *
  * @param id  field id
  * @param min minimal number of all numbers within this field
  * @param max maximal number of all numbers within this field
  * @param avg average number of all numbers within this field
  */
case class FieldNumericDetail(id: Int, min: Double, max: Double, avg: Double)

/**
  * Field data types
  */
sealed trait FieldType

object NominalFieldType extends FieldType

object NumericFieldType extends FieldType