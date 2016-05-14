package cz.vse.easyminer.preprocessing

/**
 * Created by propan on 18. 12. 2015.
 */
trait AttributeBuilder {

  val dataset: DatasetDetail

  val attribute: Attribute

  def build: AttributeDetail

}

trait CollectiveAttributeBuilder[T <: Attribute] {

  val dataset: DatasetDetail

  val attributes: Seq[T]

  def build: Seq[AttributeDetail]

}