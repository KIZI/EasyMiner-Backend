package cz.vse.easyminer.core.db

import java.io.Closeable

import cz.vse.easyminer.core.UserDatabase

/**
 * Created by propan on 16. 8. 2015.
 */
trait DBConnector[T] extends Closeable {

  val dbSettings: UserDatabase

  def DBConn: T

}

object DBConnector {

  object Exceptions {

    object NoDatabaseSchema extends Exception("Database schema has not been created yet.")

    object UnknownDBConnector extends Exception("Unknown database connector.")

  }

}

trait DBConnectors extends Closeable {

  def connector[A <: DBConnector[_]](dbType: DBType): A

}

sealed trait DBType

object LimitedDBType extends DBType

object UnlimitedDBType extends DBType
