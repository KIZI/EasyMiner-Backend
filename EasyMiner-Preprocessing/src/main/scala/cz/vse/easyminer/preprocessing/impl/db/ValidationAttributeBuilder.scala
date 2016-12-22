package cz.vse.easyminer.preprocessing.impl.db

import cz.vse.easyminer.core.Validator
import cz.vse.easyminer.core.util.CollectionValidators
import cz.vse.easyminer.preprocessing._
import cz.vse.easyminer.preprocessing.impl.Validators.AttributeValidators

/**
 * Created by propan on 29. 1. 2016.
 */
class ValidationAttributeBuilder[T <: Attribute](attributeBuilder: AttributeBuilder[T]) extends AttributeBuilder[T] with AttributeValidators {

  val dataset: DatasetDetail = attributeBuilder.dataset

  val attributes: Seq[T] = attributeBuilder.attributes

  def build: Seq[AttributeDetail] = {
    Validator(attributes)
    val result = attributeBuilder.build
    Validator(result)(CollectionValidators.NonEmpty)
    result
  }

}