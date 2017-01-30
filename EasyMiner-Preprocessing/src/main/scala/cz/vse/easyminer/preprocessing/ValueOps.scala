package cz.vse.easyminer.preprocessing

/**
 * Created by propan on 18. 12. 2015.
 */
trait ValueOps {

  val dataset: DatasetDetail

  val attribute: AttributeDetail

  def getValues(offset: Int, limit: Int): Seq[ValueDetail]

}
