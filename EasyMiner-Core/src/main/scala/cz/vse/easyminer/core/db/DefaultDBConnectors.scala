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
class DefaultDBConnectors(mysqlUserDatabase: MysqlUserDatabase, hiveUserDatabase: Option[HiveUserDatabase]) extends DBConnectors {

  protected[this] def afterConnection(dBConnector: DBConnector[_]) = {}

  private val mysqlConnector = lazily {
    val conn = new MysqlDBConnector(mysqlUserDatabase)
    afterConnection(conn)
    conn
  }

  def connector[A <: DBConnector[_]](dbType: DBType): A = dbType match {
    case LimitedDBType => mysqlConnector.apply().asInstanceOf[A]
    case UnlimitedDBType => ???
    case _ => throw DBConnector.Exceptions.UnknownDBConnector
  }

  def close(): Unit = {
    if (mysqlConnector.isEvaluated) mysqlConnector.close()
  }

}
