package cz.vse.easyminer.preprocessing.impl.db

import cz.vse.easyminer.core.TaskStatusProcessor
import cz.vse.easyminer.core.db.DBConnectors
import cz.vse.easyminer.preprocessing.DatasetType.DatasetTypeOps
import cz.vse.easyminer.preprocessing.impl.db.mysql.MysqlDatasetTypeOps
import cz.vse.easyminer.preprocessing.{LimitedDatasetType, UnlimitedDatasetType}

/**
 * Created by propan on 15. 2. 2016.
 */
object DatasetTypeConversions {

  import scala.language.implicitConversions

  implicit def limitedDatasetTypeToMysqlDatasetTypeOps(limitedDatasetType: LimitedDatasetType.type)
                                                      (implicit dBConnectors: DBConnectors,
                                                       taskStatusProcessor: TaskStatusProcessor = TaskStatusProcessor.EmptyTaskStatusProcessor)
  : DatasetTypeOps[LimitedDatasetType.type] = new MysqlDatasetTypeOps()

  implicit def unlimitedDatasetTypeToHiveDatasetTypeOps(unlimitedDatasetType: UnlimitedDatasetType.type)
                                                       (implicit dBConnectors: DBConnectors,
                                                        taskStatusProcessor: TaskStatusProcessor = TaskStatusProcessor.EmptyTaskStatusProcessor)
  : DatasetTypeOps[UnlimitedDatasetType.type] = ???

}
