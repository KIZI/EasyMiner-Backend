/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.preprocessing.impl.db

import cz.vse.easyminer.core.Validator
import cz.vse.easyminer.preprocessing.impl.Validators.AttributeValidators
import cz.vse.easyminer.preprocessing._

/**
  * Created by Vaclav Zeman on 29. 1. 2016.
  */

/**
  * This is a decorator for attribute operations object which validates all input parameters and data
  *
  * @param attributeOps attribute operations object
  */
class ValidationAttributeOps(attributeOps: AttributeOps) extends AttributeOps with AttributeValidators {

  val dataset: DatasetDetail = attributeOps.dataset

  def renameAttribute(attributeId: Int, newName: String): Unit = {
    Validator(SimpleAttribute(newName, 0))
    attributeOps.renameAttribute(attributeId, newName)
  }

  def getAttribute(attributeId: Int): Option[AttributeDetail] = attributeOps.getAttribute(attributeId)

  def deleteAttribute(attributeId: Int): Unit = attributeOps.deleteAttribute(attributeId)

  def getAllAttributes: List[AttributeDetail] = attributeOps.getAllAttributes

}