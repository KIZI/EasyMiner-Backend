package cz.vse.easyminer.preprocessing

/**
 * Created by propan on 18. 12. 2015.
 */
sealed trait ValueDetail {
  val id: Int
  val attribute: Int
  val frequency: Int
}

case class NominalValueDetail(id: Int, attribute: Int, value: String, frequency: Int) extends ValueDetail

case class NumericValueDetail(id: Int, attribute: Int, original: String, value: Double, frequency: Int) extends ValueDetail

case class NullValueDetail(id: Int, attribute: Int, frequency: Int) extends ValueDetail