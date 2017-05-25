/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.preprocessing

/**
 * Created by Vaclav Zeman on 18. 12. 2015.
 */
trait AttributeOps {

  val dataset: DatasetDetail

  def renameAttribute(attributeId: Int, newName: String): Unit

  def deleteAttribute(attributeId: Int): Unit

  def getAllAttributes: List[AttributeDetail]

  def getAttribute(attributeId: Int): Option[AttributeDetail]

}
