package cz.vse.easyminer.preprocessing.impl.db

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.data.FieldOps
import cz.vse.easyminer.data.impl.db.mysql.{MysqlDataSourceOps, MysqlFieldOps}
import cz.vse.easyminer.preprocessing.DatasetDetail

/**
  * Created by propan on 2. 10. 2016.
  */
package object mysql {

  implicit class DatasetDetailToOpsConversions(datasetDetail: DatasetDetail)(implicit mysqlDBConnector: MysqlDBConnector) {

    def toFieldOps: Option[FieldOps] = MysqlDataSourceOps().getDataSource(datasetDetail.dataSource) map MysqlFieldOps.apply

  }

}
