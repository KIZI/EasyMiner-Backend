package cz.vse.easyminer.data.impl.db

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.data._

/**
  * Created by propan on 9. 12. 2015.
  */
trait DbDataSourceOps extends DataSourceOps {

  implicit private[db] val mysqlDBConnector: MysqlDBConnector

}
