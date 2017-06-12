/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.preprocessing

import cz.vse.easyminer.data.NominalValue
import cz.vse.easyminer.preprocessing.ValueMapperOps.{ItemMapper, ValueMapper}

/**
  * Created by Vaclav Zeman on 13. 2. 2016.
  */

/**
  * All values within instances/transactions in a dataset are replaced by value id for easier manipulation with data.
  * If we obtain some rules from these data we get values as IDs, therefore we need to map these IDs back to values or vice versa.
  */
trait ValueMapperOps {

  val dataset: DatasetDetail

  /**
    * For a collection of couples attribute-value, create a value mapper which maps attribute-value pair to value id.
    *
    * @param values map which represents collection of couples attribute-value
    * @return value mapper
    */
  def valueMapper(values: Map[AttributeDetail, Set[NominalValue]]): ValueMapper

  /**
    * For a collection of couples attribute-valueId, create a value mapper which maps attribute-valueId pair to a specific value.
    *
    * @param items map which represents collection of couples attribute-valueId
    * @return value mapper
    */
  def itemMapper(items: Map[AttributeDetail, Set[Int]]): ItemMapper

}

object ValueMapperOps {

  trait ValueMapper {
    def item(attributeDetail: AttributeDetail, value: NominalValue): Option[Int]
  }

  trait ItemMapper {
    def value(attributeDetail: AttributeDetail, item: Int): Option[NominalValue]
  }

}