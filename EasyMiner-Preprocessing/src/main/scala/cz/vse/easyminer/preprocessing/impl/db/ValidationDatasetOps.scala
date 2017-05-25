/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.preprocessing.impl.db

import cz.vse.easyminer.core.Validator
import cz.vse.easyminer.data.{LimitedDataSourceType, DataSourceDetail}
import cz.vse.easyminer.preprocessing.impl.Validators.DatasetValidators
import cz.vse.easyminer.preprocessing.{Dataset, DatasetDetail, DatasetOps}

/**
 * Created by Vaclav Zeman on 29. 1. 2016.
 */
class ValidationDatasetOps(datasetOps: DatasetOps) extends DatasetOps with DatasetValidators {

  def renameDataset(datasetId: Int, newName: String): Unit = {
    Validator(Dataset(newName, DataSourceDetail(0, "", LimitedDataSourceType, 0, true)))
    datasetOps.renameDataset(datasetId, newName)
  }

  def deleteDataset(datasetId: Int): Unit = datasetOps.deleteDataset(datasetId)

  def getDataset(datasetId: Int): Option[DatasetDetail] = datasetOps.getDataset(datasetId)

  def getAllDatasets: List[DatasetDetail] = datasetOps.getAllDatasets

}
