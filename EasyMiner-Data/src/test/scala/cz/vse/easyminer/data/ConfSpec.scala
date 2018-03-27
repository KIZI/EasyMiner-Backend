package cz.vse.easyminer.data

import cz.vse.easyminer.core.util.Conf
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

  it should "return strings for user settings" in {
    Conf().get[String]("easyminer.user.http-endpoint") should not be empty
    Conf().get[String]("easyminer.user.auth-path") should not be empty
    Conf().get[String]("easyminer.user.limited-db-path") should not be empty
  }

  it should "return strings for data settings" in {
    Conf().get[String]("easyminer.data.rest.address") should not be empty
    Conf().get[Int]("easyminer.data.rest.port") should be > 0
    Conf().get[String]("easyminer.data.table-prefix") should not be empty
    Conf().get[Int]("easyminer.data.table-name-maxlen") should be > 0
    Conf().get[Int]("easyminer.data.table-col-maxlen") should be > 0
  }

}
