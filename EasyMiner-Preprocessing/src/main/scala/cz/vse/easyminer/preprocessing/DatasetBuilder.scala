package cz.vse.easyminer.preprocessing

/**
 * Created by propan on 18. 12. 2015.
 */
trait DatasetBuilder {

  val dataset: Dataset

  def build: DatasetDetail

}
