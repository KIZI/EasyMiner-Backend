/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.core.db

import cz.vse.easyminer.core.MysqlUserDatabase
import cz.vse.easyminer.core.util.Conf
import scalikejdbc._

import scala.concurrent.duration.Duration
import scala.language.{implicitConversions, postfixOps}

/**
  * Created by Vaclav Zeman on 8. 8. 2015.
  */

/**
  * Mysql database connection
  * This connection is managed by scalikeJDBC.
  * We need to create connection pool for any user. For each user there are some maximal number of parallel connections in the pool.
  *
  * @param dbSettings database connection settings for some user
  */
class MysqlDBConnector(val dbSettings: MysqlUserDatabase) extends DBConnector[DBConnection] {

  /**
    * connectio pool name for scalikeJDBC
    */
  private val connectionPoolName = s"mysql-${dbSettings.dbServer}-${dbSettings.dbUser}-${dbSettings.dbName}"

  /**
    * If this instance is created then it also creates connection pool for the user
    */
  ConnectionPool.add(
    connectionPoolName,
    s"jdbc:mysql://${dbSettings.dbServer}:3306/${dbSettings.dbName}?characterEncoding=utf8&rewriteBatchedStatements=true",
    dbSettings.dbUser,
    dbSettings.dbPassword,
    MysqlDBConnector.connectionPoolSettings
  )

  /**
    * This closes all connections in the pool
    */
  def close(): Unit = ConnectionPool.close(connectionPoolName)

  /**
    * Get scalikeJDBC database connection
    * This function is lazy. It does not create a connection in the pool, but it only prepares it
    *
    * @return lazy database connection
    */
  def DBConn: DBConnection = NamedDB(connectionPoolName).toDB()

}

/**
  * Basic settings for mysql connection
  */
object MysqlDBConnector {

  Class.forName("com.mysql.jdbc.Driver")

  GlobalSettings.loggingSQLAndTime = LoggingSQLAndTimeSettings(
    enabled = true,
    singleLineMode = true,
    printUnprocessedStackTrace = false,
    stackTraceDepth = 15,
    logLevel = 'info,
    warningEnabled = true,
    warningThresholdMillis = 3000L,
    warningLogLevel = 'warn
  )

  val connectionPoolSettings = ConnectionPoolSettings(
    initialSize = 0,
    maxSize = Conf().get[Int]("easyminer.db.max-connection-pool-size"),
    connectionTimeoutMillis = Conf().opt[Duration]("easyminer.db.connection-timeout").map(_.toMillis).getOrElse(-1),
    validationQuery = "select 1 as one",
    connectionPoolFactoryName = "commons-dbcp2"
  )

  /**
    * Implicit conversion of database connectors into mysql database connector
    *
    * @param dBConnectors database connectors
    * @return mysql database connector
    */
  implicit def dBConnectorsToMysqlDbConnector(dBConnectors: DBConnectors): MysqlDBConnector = dBConnectors.connector(LimitedDBType)

}