/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data

/**
  * Created by Vaclav Zeman on 13. 8. 2015.
  */

/**
  * Traits for an instance
  * Instance is the smallest piece of information in transactional database
  * There are information about id of transaction (row number), field id (col number) and value
  */
sealed trait Instance {
  val id: Int
  val field: Int
  val value: Value
}

case class NumericInstance(id: Int, field: Int, value: NumericValue) extends Instance

case class NominalInstance(id: Int, field: Int, value: NominalValue) extends Instance

/**
  * This object represents one transaction in transactional database
  *
  * @param id     id of trainsaction
  * @param values items in transaction (field-value pairs)
  */
case class AggregatedInstance(id: Int, values: Seq[AggregatedInstanceItem])

/**
  * This object represents one item in form field-value pair
  *
  * @param field field of this value (column)
  * @param value value (numeric or nominal)
  */
case class AggregatedInstanceItem(field: Int, value: Value)