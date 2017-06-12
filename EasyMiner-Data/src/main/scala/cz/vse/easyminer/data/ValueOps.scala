/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data

/**
  * Created by Vaclav Zeman on 20. 8. 2015.
  */

/**
  * All operations for existed values within a data source and field
  */
trait ValueOps extends ValueHistogramOps {

  /**
    * Data source detail
    */
  val dataSource: DataSourceDetail

  /**
    * Field detail
    */
  val field: FieldDetail

  /**
    * Get all values for the data source and field
    *
    * @param offset first record is 0 (not 1)
    * @param limit  it is restricted by the maximal limit value
    * @return seq of value details
    */
  def getValues(offset: Int, limit: Int): Seq[ValueDetail]

  /**
    * Get numeric stats of this field
    *
    * @return stats object or None if the field is not numeric
    */
  def getNumericStats: Option[FieldNumericDetail]

}
