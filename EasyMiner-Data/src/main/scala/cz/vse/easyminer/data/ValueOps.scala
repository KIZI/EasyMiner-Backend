package cz.vse.easyminer.data

/**
 * Created by propan on 20. 8. 2015.
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
