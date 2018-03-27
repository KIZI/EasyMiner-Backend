package cz.vse.easyminer.preprocessing

import cz.vse.easyminer.core.util.Conf
import cz.vse.easyminer.core.{HiveUserDatabase, MysqlUserDatabase}
import org.scalatest.{FlatSpec, Matchers}

/**
 * Created by propan on 10. 8. 2015.
 */
class ConfSpec extends FlatSpec with Matchers with ConfOpt {

  "Conf" should "return strings for test db settings" in {
    dbserver should not be empty
    dbuser should not be empty
    dbpassword should not be empty
    dbname should not be empty
    hiveserver should not be empty
    hiveuser should not be empty
    hiveport should be > 0
    hivename should not be empty
  }

  it should "return strings for preprocessing settings" in {
    Conf().get[String]("easyminer.preprocessing.rest.address") should not be empty
    Conf().get[Int]("easyminer.preprocessing.rest.port") should be > 0
    Conf().get[String]("easyminer.preprocessing.table-prefix") should not be empty
    Conf().get[Int]("easyminer.preprocessing.table-name-maxlen") should be > 0
    Conf().get[Int]("easyminer.preprocessing.table-col-maxlen") should be > 0
  }

}

trait ConfOpt {

  def dbserver = Conf().get[String]("db.server")

  def dbuser = Conf().get[String]("db.user")

  def dbpassword = Conf().get[String]("db.password")

  def dbname = Conf().get[String]("db.name")

  def hiveserver = Conf().get[String]("hive.server")

  def hiveuser = Conf().get[String]("hive.user")

  def hiveport = Conf().get[Int]("hive.port")

  def hivename = Conf().get[String]("hive.name")

  def mysqlUserDatabase = MysqlUserDatabase(dbserver, dbname, dbuser, dbpassword)

  def hiveUserDatabase = HiveUserDatabase(hiveserver, hiveport, hivename, hiveuser)

}