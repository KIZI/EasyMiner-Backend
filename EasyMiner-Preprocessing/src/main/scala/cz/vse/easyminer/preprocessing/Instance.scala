/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.preprocessing

/**
  * Created by Vaclav Zeman on 27. 1. 2016.
  */

/**
  * This object represents one instance/transaction of the transactional database
  *
  * @param id     transaction id
  * @param values items of the transaction
  */
case class Instance(id: Int, values: Seq[Item])

/**
  * This represents one item within one transaction
  *
  * @param id        transaction id
  * @param attribute attribute id
  * @param value     value id
  */
case class NarrowInstance(id: Int, attribute: Int, value: Int)

/**
  * Item which is consisted of the couple attribute-value
  *
  * @param attribute attribute id
  * @param value     value id
  */
case class Item(attribute: Int, value: Int)

case class InstanceWithValue(id: Int, values: Seq[InstanceItemWithValue])

case class InstanceItemWithValue(attribute: Int, value: String)