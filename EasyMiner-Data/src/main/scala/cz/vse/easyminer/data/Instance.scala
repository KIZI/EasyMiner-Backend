package cz.vse.easyminer.data

/**
 * Created by propan on 13. 8. 2015.
 */
case class Instance(id: Int, values: Seq[Value])

case class Instances(fields: Seq[FieldDetail], instances: Seq[Instance])

sealed trait NarrowInstance {
  val id: Int
  val field: Int
  val value: Value
}

case class NumericNarrowInstance(id: Int, field: Int, value: NumericValue) extends NarrowInstance

case class NominalNarrowInstance(id: Int, field: Int, value: NominalValue) extends NarrowInstance