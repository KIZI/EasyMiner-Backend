package cz.vse.easyminer.preprocessing.impl.db

import cz.vse.easyminer.core.TaskStatusProcessor
import cz.vse.easyminer.core.db.{MysqlDBConnector, DBConnectors}
import cz.vse.easyminer.preprocessing.DatasetType.DatasetTypeOps
import cz.vse.easyminer.preprocessing.impl.db.hive.HiveDatasetTypeOps
import cz.vse.easyminer.preprocessing.{UnlimitedDatasetType, LimitedDatasetType}
import cz.vse.easyminer.preprocessing.impl.db.mysql.MysqlDatasetTypeOps

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
  : DatasetTypeOps[UnlimitedDatasetType.type] = new HiveDatasetTypeOps()

}
