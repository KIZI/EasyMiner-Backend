package cz.vse.easyminer.preprocessing

/**
 * Created by propan on 18. 12. 2015.
 */
trait AttributeOps {

  val dataset: DatasetDetail

  def renameAttribute(attributeId: Int, newName: String): Unit

  def deleteAttribute(attributeId: Int): Unit

  def getAllAttributes: List[AttributeDetail]

  def getAttribute(attributeId: Int): Option[AttributeDetail]

}
