package cz.vse.easyminer.miner

import com.typesafe.config.ConfigException
import cz.vse.easyminer.core.{HiveUserDatabase, MysqlUserDatabase}
import cz.vse.easyminer.core.util.Conf
import org.scalatest._

class ConfSpec extends FlatSpec with Matchers with ConfOpt {

  "Conf.get" should "return exception with empty string" in {
    intercept[ConfigException.BadPath] {
      Conf().get[String]("")
    }
  }

  it should "return string for rest.address" in {
    Conf().get[String]("easyminer.miner.rest.address") should not be empty
  }

  it should "return int for rest.port" in {
    val x = Conf().get[Int]("easyminer.miner.rest.port")
    x should be > 0
  }

  it should "return string for r-miner.rserve-address" in {
    rserveAddress should not be empty
  }

  it should "return string for jdbc-driver-dir-absolute-path" in {
    jdbcdriver should not be empty
  }

  it should "return int or exception Missing for r-miner.rserve-port" in {
    try {
      rservePort should be > 0
    } catch {
      case _: ConfigException.Missing => fail()
    }
  }

  "Conf.opt" should "be None for unexisted attribute" in {
    Conf().opt[String]("unexisted") should be(None)
  }

  it should "be Some(String) for rest.address" in {
    Conf().opt[String]("easyminer.miner.rest.address") should be('defined)
  }

  "Conf.getOrElse" should "return default string for an unexisted attribute" in {
    Conf().getOrElse[String]("unexisted", "default") should be("default")
  }

  it should "not be default string for rest.address" in {
    Conf().getOrElse[String]("easyminer.miner.rest.address", "default") should not be "default"
  }

}

trait ConfOpt {

  def jdbcdriver = Conf().get[String]("easyminer.miner.r.jdbc-driver-dir-absolute-path")

  def rserveAddress = Conf().get[String]("easyminer.miner.r.rserve-address")

  def rservePort = Conf().get[Int]("easyminer.miner.r.rserve-port")

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