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
  * Value object which is saved in database as a value of some attribute and dataset
  *
  * @param id        value id
  * @param attribute attribute id
  * @param value     value (string for nominal or interval/number for numeric)
  * @param frequency how many instances/transactions this value covers within the transactional database
  */
case class ValueDetail(id: Int, attribute: Int, value: String, frequency: Int)