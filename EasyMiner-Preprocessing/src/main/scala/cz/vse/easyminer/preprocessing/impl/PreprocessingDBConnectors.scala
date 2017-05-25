/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.preprocessing.impl

import cz.vse.easyminer.core.db.{MysqlDBConnector, DBConnector, DefaultDBConnectors}
import cz.vse.easyminer.core.util.Match
import cz.vse.easyminer.core.{HiveUserDatabase, MysqlUserDatabase}
import cz.vse.easyminer.preprocessing.impl.db.mysql.MysqlSchemaOps

/**
 * Created by Vaclav Zeman on 24. 8. 2015.
 */
class PreprocessingDBConnectors(mysqlUserDatabase: MysqlUserDatabase, hiveUserDatabase: Option[HiveUserDatabase]) extends DefaultDBConnectors(mysqlUserDatabase, hiveUserDatabase) {

  override protected[this] def afterConnection(dBConnector: DBConnector[_]): Unit = Match(dBConnector) {
    case mysqlDbConnector: MysqlDBConnector =>
      val schemaOps = MysqlSchemaOps()(mysqlDbConnector)
      if (!schemaOps.schemaExists) {
        schemaOps.createSchema()
      }
  }

}