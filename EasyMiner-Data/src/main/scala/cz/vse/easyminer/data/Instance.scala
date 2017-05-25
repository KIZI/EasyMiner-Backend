/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data

/**
  * Created by Vaclav Zeman on 13. 8. 2015.
  */
sealed trait Instance {
  val id: Int
  val field: Int
  val value: Value
}

case class NumericInstance(id: Int, field: Int, value: NumericValue) extends Instance

case class NominalInstance(id: Int, field: Int, value: NominalValue) extends Instance

case class AggregatedInstance(id: Int, values: Seq[AggregatedInstanceItem])

case class AggregatedInstanceItem(field: Int, value: Value)