package cz.vse.easyminer.preprocessing.impl.db

import cz.vse.easyminer.core.Validator
import cz.vse.easyminer.preprocessing._
import cz.vse.easyminer.preprocessing.impl.Validators.AttributeValidators

/**
 * Created by propan on 29. 1. 2016.
 */
class ValidationAttributeBuilder(attributeBuilder: AttributeBuilder) extends AttributeBuilder with AttributeValidators {

  val dataset: DatasetDetail = attributeBuilder.dataset

  val attribute: Attribute = attributeBuilder.attribute

  def build: AttributeDetail = {
    Validator(attribute)
    attributeBuilder.build
  }

}