/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data.impl

import cz.vse.easyminer.core.db.{DefaultDBConnectors, DBConnector, MysqlDBConnector}
import cz.vse.easyminer.core.util.Match
import cz.vse.easyminer.core.{HiveUserDatabase, MysqlUserDatabase}
import cz.vse.easyminer.data.impl.db.mysql.MysqlSchemaOps

/**
  * Created by Vaclav Zeman on 24. 8. 2015.
  */

/**
  * Implementation for database connections where the method afterConnection is implemented
  *
  * @param mysqlUserDatabase mysql connection settings
  * @param hiveUserDatabase  hive connection settings (but now it is not supported for open-source version)
  */
class DataDBConnectors(mysqlUserDatabase: MysqlUserDatabase, hiveUserDatabase: Option[HiveUserDatabase]) extends DefaultDBConnectors(mysqlUserDatabase, hiveUserDatabase) {

  /**
    * After connection it checks whether the database schema exists; if not then create it.
    *
    * @param dBConnector database connection
    */
  override protected[this] def afterConnection(dBConnector: DBConnector[_]): Unit = Match(dBConnector) {
    case mysqlDbConnector: MysqlDBConnector =>
      val schemaOps = new MysqlSchemaOps()(mysqlDbConnector)
      if (!schemaOps.schemaExists) {
        schemaOps.createSchema()
      }
  }

}