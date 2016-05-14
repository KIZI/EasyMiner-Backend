package cz.vse.easyminer.preprocessing.impl.db

import cz.vse.easyminer.core.Validator
import cz.vse.easyminer.preprocessing.impl.Validators.AttributeValidators
import cz.vse.easyminer.preprocessing._

/**
 * Created by propan on 29. 1. 2016.
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