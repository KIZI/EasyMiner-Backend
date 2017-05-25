/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.preprocessing.impl.db

import cz.vse.easyminer.core.Validator
import cz.vse.easyminer.preprocessing.impl.Validators.DatasetValidators
import cz.vse.easyminer.preprocessing.{Dataset, DatasetBuilder, DatasetDetail}

/**
 * Created by Vaclav Zeman on 29. 1. 2016.
 */
class ValidationDatasetBuilder(datasetBuilder: DatasetBuilder) extends DatasetBuilder with DatasetValidators {

  val dataset: Dataset = datasetBuilder.dataset

  def build: DatasetDetail = {
    Validator(dataset)
    datasetBuilder.build
  }

}