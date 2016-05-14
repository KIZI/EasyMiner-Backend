package cz.vse.easyminer.core

/**
 * Created by propan on 8. 8. 2015.
 */
case class User(id: Int, name: String, email: String)

trait UserDatabase {
  val dbServer: String
  val dbName: String
}

case class MysqlUserDatabase(dbServer: String, dbName: String, dbUser: String, dbPassword: String) extends UserDatabase

case class HiveUserDatabase(dbServer: String, dbPort: Int, dbName: String, dbUser: String) extends UserDatabase