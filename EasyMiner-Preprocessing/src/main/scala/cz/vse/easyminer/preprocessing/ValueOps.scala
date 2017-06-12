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
  * All operations for existed values within an attribute and dataset
  */
trait ValueOps {

  val dataset: DatasetDetail

  val attribute: AttributeDetail

  /**
    * Get all values for a specific dataset and attribute
    *
    * @param offset start pointer
    * @param limit  number of records to retrieve
    * @return value details
    */
  def getValues(offset: Int, limit: Int): Seq[ValueDetail]

}
