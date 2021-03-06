/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.preprocessing.impl.db

import cz.vse.easyminer.core.Validator
import cz.vse.easyminer.core.util.CollectionValidators
import cz.vse.easyminer.preprocessing._
import cz.vse.easyminer.preprocessing.impl.Validators.AttributeValidators

/**
  * Created by Vaclav Zeman on 29. 1. 2016.
  */

/**
  * This is a decorator for attribute builder which validates all input parameters and data
  *
  * @param attributeBuilder attribute builder
  * @tparam T type of attribute preprocessings
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