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
  * All operations for existed datasets of a specific user connection
  */
trait DatasetOps {

  /**
    * Rename dataset
    *
    * @param datasetId dataset id
    * @param newName   new name
    */
  def renameDataset(datasetId: Int, newName: String): Unit

  /**
    * Get dataset by ID
    *
    * @param datasetId dataset ID
    * @return dataset detail or None if dataset does not exist
    */
  def getDataset(datasetId: Int): Option[DatasetDetail]

  /**
    * Delete dataset
    *
    * @param datasetId dataset id
    */
  def deleteDataset(datasetId: Int): Unit

  /**
    * Get all existed dataset
    *
    * @return list of datasets
    */
  def getAllDatasets: List[DatasetDetail]

}
