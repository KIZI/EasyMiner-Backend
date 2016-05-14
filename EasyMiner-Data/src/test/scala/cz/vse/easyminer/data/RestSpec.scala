package cz.vse.easyminer.data

import akka.actor.Props
import akka.io.IO
import cz.vse.easyminer.core.util.BasicFunction._
import cz.vse.easyminer.core.util.{Match, Conf}
import cz.vse.easyminer.data.impl.db.mysql.MysqlSchemaOps
import cz.vse.easyminer.data.rest.DataMainService
import org.scalatest._
import spray.can.Http
import spray.http.HttpHeaders.Location
import spray.http.HttpRequest
import spray.httpx.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, JsString, JsObject}
import spray.routing.RequestContext
import spray.testkit.ScalatestRouteTest
import scala.concurrent.duration._

import scala.collection.immutable.IndexedSeq

/**
 * Created by propan on 17. 8. 2015.
 */
class RestSpec extends Suites() with FlatSpecLike with ScalatestRouteTest with Matchers with BeforeAndAfterAll {

  import DefaultJsonProtocol._
  import SprayJsonSupport._

  implicit val routeTestTimeout = RouteTestTimeout(60.second)

  override val nestedSuites: IndexedSeq[Suite] = Vector(
    new MainServiceSpec(this),
    new UploadServiceSpec(this),
    new ValueServiceSpec(this),
    new FieldServiceSpec(this),
    new DataSourceServiceSpec(this)
  )

  val service = {
    val service = system.actorOf(Props[DataMainService], "main-service")
    IO(Http) ! Http.Bind(service, Conf().get[String]("easyminer.data.rest.address"), port = Conf().get[Int]("easyminer.data.rest.port"))
    service
  }

  val authorizedRequest = addHeader("Authorization", "ApiKey 123456")

  def testTask[T](request: HttpRequest, times: Int = 50, expectedStatusCode: Int = 201)(body: => T): Option[T] = {
    val taskIdOpt = authorizedRequest(request) ~> route ~> check {
      response.status.intValue shouldBe 202
      response.headers.filter(_.isInstanceOf[Location]) should not be empty
      responseAs[JsObject].fields.get("taskId").collect {
        case JsString(x) => x
      }
    }
    taskIdOpt should not be empty
    for (taskId <- taskIdOpt) yield {
      limitedRepeatUntil[Int](times)(_ == expectedStatusCode)(authorizedRequest(Get(s"/api/v1/task-status/$taskId")) ~> route ~> check {
        val responseStatus = response.status.intValue
        Match(responseStatus) {
          case 200 => responseAs[JsObject].fields.get("taskId") shouldBe Some(JsString(taskId))
          case 201 => response.headers.filter(_.isInstanceOf[Location]) should not be empty
        }
        responseStatus
      }) shouldBe expectedStatusCode
      authorizedRequest(Get(s"/api/v1/task-result/$taskId")) ~> route ~> check(body)
    }
  }

  override protected def runNestedSuites(args: Args): Status = super.runNestedSuites(args.copy(distributor = None))

  private def cleanDatabase() = {
    implicit val mysqlConnection = DBSpec.makeMysqlConnector(false)
    implicit val hiveConnection = HiveSpec.makeHiveConnector
    if (new MysqlSchemaOps().schemaExists) {
      HiveSpec.rollbackData()
      DBSpec.rollbackData()
    }
    hiveConnection.close()
    mysqlConnection.close()
  }

  override protected def beforeAll(): Unit = {
    cleanDatabase()
  }

  override protected def afterAll(): Unit = {
    cleanDatabase()
  }

  def route(rc: RequestContext) = service ! rc

}

object RestSpec {

  object SlowTest extends Tag("RestSpec.SlowTest")

  object HiveTest extends Tag("RestSpec.HiveTest")

}