package cz.vse.easyminer.preprocessing.impl.db

import cz.vse.easyminer.core.Validator
import cz.vse.easyminer.preprocessing.impl.Validators.DatasetValidators
import cz.vse.easyminer.preprocessing.{Dataset, DatasetBuilder, DatasetDetail}

/**
 * Created by propan on 29. 1. 2016.
 */
class ValidationDatasetBuilder(datasetBuilder: DatasetBuilder) extends DatasetBuilder with DatasetValidators {

  val dataset: Dataset = datasetBuilder.dataset

  def build: DatasetDetail = {
    Validator(dataset)
    datasetBuilder.build
  }

}