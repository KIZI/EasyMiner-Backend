package cz.vse.easyminer.preprocessing

/**
 * Created by propan on 18. 12. 2015.
 */
trait DatasetOps {

  def renameDataset(datasetId: Int, newName: String): Unit

  def getDataset(datasetId: Int): Option[DatasetDetail]

  def deleteDataset(datasetId: Int): Unit

  def getAllDatasets: List[DatasetDetail]

}
