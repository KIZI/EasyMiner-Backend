package cz.vse.easyminer.preprocessing.impl.db

import cz.vse.easyminer.core.db.{HiveDBConnector, MysqlDBConnector}
import cz.vse.easyminer.data.FieldOps
import cz.vse.easyminer.data.impl.db.hive.{HiveDataSourceOps, HiveFieldOps}
import cz.vse.easyminer.preprocessing.DatasetDetail

/**
  * Created by propan on 11. 5. 2016.
  */
package object hive {

  implicit class DatasetDetailToOpsConversions(datasetDetail: DatasetDetail)(implicit mysqlDBConnector: MysqlDBConnector, hiveDBConnector: HiveDBConnector) {

    def toFieldOps: Option[FieldOps] = HiveDataSourceOps().getDataSource(datasetDetail.dataSource) map HiveFieldOps.apply

  }

}
