/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.preprocessing

/**
  * Created by Vaclav Zeman on 18. 12. 2015.
  */

/**
  * All operations for existed data attributes within a dataset
  */
trait AttributeOps {

  val dataset: DatasetDetail

  /**
    * Rename attribute
    *
    * @param attributeId attribute id
    * @param newName     new name
    */
  def renameAttribute(attributeId: Int, newName: String): Unit

  /**
    * Delete attribute
    *
    * @param attributeId attribute id
    */
  def deleteAttribute(attributeId: Int): Unit

  /**
    * Get all attributes for the dataset
    *
    * @return list of attributes
    */
  def getAllAttributes: List[AttributeDetail]

  /**
    * Get attribute from an ID. If the id does not exist than return None
    *
    * @param attributeId attribute id
    * @return attribute detail or None if it does not exist
    */
  def getAttribute(attributeId: Int): Option[AttributeDetail]

}
