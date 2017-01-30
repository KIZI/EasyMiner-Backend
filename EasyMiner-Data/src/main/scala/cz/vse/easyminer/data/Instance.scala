package cz.vse.easyminer.data

/**
  * Created by propan on 13. 8. 2015.
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