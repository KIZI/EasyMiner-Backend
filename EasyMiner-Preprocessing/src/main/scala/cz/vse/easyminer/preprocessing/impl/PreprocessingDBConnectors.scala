package cz.vse.easyminer.preprocessing.impl

import cz.vse.easyminer.core.db.{MysqlDBConnector, DBConnector, DefaultDBConnectors}
import cz.vse.easyminer.core.util.Match
import cz.vse.easyminer.core.{HiveUserDatabase, MysqlUserDatabase}
import cz.vse.easyminer.preprocessing.impl.db.mysql.MysqlSchemaOps

/**
 * Created by propan on 24. 8. 2015.
 */
class PreprocessingDBConnectors(mysqlUserDatabase: MysqlUserDatabase, hiveUserDatabase: HiveUserDatabase) extends DefaultDBConnectors(mysqlUserDatabase, hiveUserDatabase) {

  override protected[this] def afterConnection(dBConnector: DBConnector[_]): Unit = Match(dBConnector) {
    case mysqlDbConnector: MysqlDBConnector =>
      val schemaOps = MysqlSchemaOps()(mysqlDbConnector)
      if (!schemaOps.schemaExists) {
        schemaOps.createSchema()
      }
  }

}