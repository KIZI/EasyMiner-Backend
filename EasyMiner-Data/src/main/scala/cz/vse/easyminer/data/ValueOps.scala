/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data

/**
 * Created by Vaclav Zeman on 20. 8. 2015.
 */
trait ValueOps extends ValueHistogramOps {

  val dataSource: DataSourceDetail

  val field: FieldDetail

  /**
   *
   * @param offset first record is 0 (not 1)
   * @param limit it is restricted by the maximal limit value
   * @return
   */
  def getValues(offset: Int, limit: Int): Seq[ValueDetail]

  def getNumericStats: Option[FieldNumericDetail]

}
