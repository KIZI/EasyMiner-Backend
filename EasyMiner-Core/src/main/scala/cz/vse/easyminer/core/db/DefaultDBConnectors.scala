/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.core.db

import cz.vse.easyminer.core.util.Lazy._
import cz.vse.easyminer.core.{HiveUserDatabase, MysqlUserDatabase}

/**
  * Created by Vaclav Zeman on 9. 3. 2016.
  */

/**
  * Default database connectors object supports only limited database type
  *
  * @param mysqlUserDatabase mysql connection settings
  * @param hiveUserDatabase  hive connection settings (but now it is not supported for open-source version)
  */
class DefaultDBConnectors(mysqlUserDatabase: MysqlUserDatabase, hiveUserDatabase: Option[HiveUserDatabase]) extends DBConnectors {

  /**
    * This function is fired after successful connection to the database
    *
    * @param dBConnector database connection
    */
  protected[this] def afterConnection(dBConnector: DBConnector[_]) = {}

  /**
    * After first call of this object, database connection is created
    */
  private val mysqlConnector = lazily {
    val conn = new MysqlDBConnector(mysqlUserDatabase)
    afterConnection(conn)
    conn
  }

  /**
    * Return a database connection for a specific database type
    *
    * @param dbType database type (limited or unlimited)
    * @tparam A desired connection type
    * @return database connector
    */
  def connector[A <: DBConnector[_]](dbType: DBType): A = dbType match {
    case LimitedDBType => mysqlConnector.apply().asInstanceOf[A]
    case UnlimitedDBType => ???
    case _ => throw DBConnector.Exceptions.UnknownDBConnector
  }

  /**
    * Close the database connection
    */
  def close(): Unit = {
    if (mysqlConnector.isEvaluated) mysqlConnector.close()
  }

}
