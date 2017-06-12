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
  * Main abstraction for creation of attributes from fields (preprocessing of fields)
  *
  * @tparam T type of attribute preprocessings
  */
trait AttributeBuilder[+T <: Attribute] {

  /**
    * Dataset detail
    */
  val dataset: DatasetDetail

  /**
    * Attributes creation definitions
    */
  val attributes: Seq[T]

  /**
    * Function which creates attributes from all definitions
    *
    * @return created attributes
    */
  def build: Seq[AttributeDetail]

}