package cz.vse.easyminer.preprocessing

/**
 * Created by propan on 18. 12. 2015.
 */
trait AttributeBuilder[+T <: Attribute] {

  val dataset: DatasetDetail

  val attributes: Seq[T]

  def build: Seq[AttributeDetail]

}