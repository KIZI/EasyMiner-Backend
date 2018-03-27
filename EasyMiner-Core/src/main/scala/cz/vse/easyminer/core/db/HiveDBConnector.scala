package cz.vse.easyminer.core.db

import java.sql.DriverManager

import cz.vse.easyminer.core.HiveUserDatabase
import cz.vse.easyminer.core.hadoop.Hadoop
import cz.vse.easyminer.core.hadoop.Hadoop.AuthType
import cz.vse.easyminer.core.util.BasicFunction.tryClose
import cz.vse.easyminer.core.util.Conf
import scalikejdbc._

import scala.concurrent.duration.Duration
import scala.language.implicitConversions

/**
 * Created by propan on 12. 10. 2015.
 */
class HiveDBConnector(val dbSettings: HiveUserDatabase) extends DBConnector[DBConnection] {

  private val connectionPoolName = s"hive-${dbSettings.dbServer}-${dbSettings.dbUser}-${dbSettings.dbName}"

  HiveDBConnector

  private def connectionUrl(database: String) = Hadoop.authType match {
    case AuthType.Kerberos =>
      val principal = Conf().get[String]("easyminer.hadoop.auth.kerberos-hive-principal")
      s"jdbc:hive2://${dbSettings.dbServer}:${dbSettings.dbPort}/$database;principal=$principal"
    case AuthType.Simple =>
      s"jdbc:hive2://${dbSettings.dbServer}:${dbSettings.dbPort}/$database"
  }

  private val userName = Hadoop.authType match {
    case AuthType.Kerberos => ""
    case AuthType.Simple => dbSettings.dbUser
  }

  tryClose(DriverManager.getConnection(connectionUrl("default"), userName, "")) { conn =>
    tryClose(conn.createStatement()) { stmt =>
      stmt.execute(s"CREATE DATABASE IF NOT EXISTS ${dbSettings.dbName}")
    }
  }

  ConnectionPool.add(
    connectionPoolName,
    connectionUrl(dbSettings.dbName),
    userName,
    "",
    MysqlDBConnector.connectionPoolSettings
  )

  def close(): Unit = ConnectionPool.close(connectionPoolName)

  def DBConn: DBConnection = {
    val namedDb = NamedDB(connectionPoolName)
    DB(new HiveConnectionProxy(namedDb.conn), namedDb.connectionAttributes)
  }

}

object HiveDBConnector {

  MysqlDBConnector

  Class.forName("org.apache.hive.jdbc.HiveDriver")

  val connectionPoolSettings = ConnectionPoolSettings(
    initialSize = 0,
    maxSize = Conf().get[Int]("easyminer.db.max-connection-pool-size"),
    connectionTimeoutMillis = Conf().opt[Duration]("easyminer.db.connection-timeout").map(_.toMillis).getOrElse(-1),
    validationQuery = "select 1 as one",
    connectionPoolFactoryName = "commons-dbcp2"
  )

  implicit def dBConnectorsToHiveDbConnector(dBConnectors: DBConnectors): HiveDBConnector = dBConnectors.connector(UnlimitedDBType)

}
