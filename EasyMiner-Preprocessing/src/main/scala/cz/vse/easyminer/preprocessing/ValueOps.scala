package cz.vse.easyminer.preprocessing

import cz.vse.easyminer.data.ValueHistogramOps

/**
 * Created by propan on 18. 12. 2015.
 */
trait ValueOps extends ValueHistogramOps {

  val dataset: DatasetDetail

  val attribute: AttributeDetail

  def getValues(offset: Int, limit: Int): Seq[ValueDetail]

  def getNumericStats: Option[AttributeNumericDetail]

}
