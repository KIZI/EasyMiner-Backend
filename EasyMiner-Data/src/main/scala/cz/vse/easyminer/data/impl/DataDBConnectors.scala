package cz.vse.easyminer.data.impl

import cz.vse.easyminer.core.db.{DefaultDBConnectors, DBConnector, MysqlDBConnector}
import cz.vse.easyminer.core.util.Match
import cz.vse.easyminer.core.{HiveUserDatabase, MysqlUserDatabase}
import cz.vse.easyminer.data.impl.db.mysql.MysqlSchemaOps

/**
 * Created by propan on 24. 8. 2015.
 */
class DataDBConnectors(mysqlUserDatabase: MysqlUserDatabase, hiveUserDatabase: Option[HiveUserDatabase]) extends DefaultDBConnectors(mysqlUserDatabase, hiveUserDatabase) {

  override protected[this] def afterConnection(dBConnector: DBConnector[_]): Unit = Match(dBConnector) {
    case mysqlDbConnector: MysqlDBConnector =>
      val schemaOps = new MysqlSchemaOps()(mysqlDbConnector)
      if (!schemaOps.schemaExists) {
        schemaOps.createSchema()
      }
  }

}