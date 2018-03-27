package cz.vse.easyminer.miner

import akka.actor.Props
import akka.io.IO
import cz.vse.easyminer.core.util.{BasicFunction, Conf}
import cz.vse.easyminer.miner.impl.JsonFormatters.JsonOutlierWithInstance._
import cz.vse.easyminer.miner.rest.MinerMainService
import org.scalatest.{FlatSpec, Inspectors, Matchers, Tag}
import spray.can.Http
import spray.httpx.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, JsArray, JsNumber, JsObject, JsString}
import spray.routing.RequestContext
import spray.testkit.ScalatestRouteTest

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.xml.{Elem, Node, NodeSeq}

/**
  * Created by propan on 29. 2. 2016.
  */
class RestSpec extends FlatSpec with Matchers with Inspectors with ScalatestRouteTest with TemplateOpt with SprayJsonSupport with DefaultJsonProtocol {

  implicit val routeTestTimeout = RouteTestTimeout(60.second)

  val service = {
    val service = system.actorOf(Props[MinerMainService], "main-service")
    IO(Http) ! Http.Bind(service, Conf().get[String]("easyminer.miner.rest.address"), port = Conf().get[Int]("easyminer.miner.rest.port"))
    service
  }

  val authorizedRequest = addHeader("Authorization", "ApiKey 123456")

  var taskId: Option[String] = None

  def route(rc: RequestContext) = service ! rc

  def removeXmlElements(xml: Node, elemNames: String*): Node = xml match {
    case elem: Elem =>
      val children = elem.child.filter(x => !elemNames.contains(x.label)).map(x => removeXmlElements(x, elemNames: _*))
      elem.copy(child = children)
    case _ => xml
  }

  "MinerService" should "start mine with some PMML" in {
    import MysqlPreprocessingDbOps.datasetAudiology._
    authorizedRequest(Post("/api/v1/mine", inputPmmlAudiology(datasetDetail, attributes, InterestMeasures(Confidence(0.5), Support(0.001), Limit(100), MaxRuleLength(8))))) ~> route ~> check {
      response.status.intValue shouldBe 202
      taskId = Some((responseAs[NodeSeq] \\ "task-id").text)
    }
  }

  it should "pick up partial results" in {
    taskId should not be empty
    val code = BasicFunction.repeatUntil[Int]()(code => code != 206 && code != 204) {
      authorizedRequest(Get("/api/v1/partial-result/" + taskId.get)) ~> route ~> check {
        val code = response.status.intValue
        if (code == 206) {
          val contingencyTables = (responseAs[NodeSeq] \\ "FourFtTable").map { node =>
            ContingencyTable((node \@ "a").toInt, (node \@ "b").toInt, (node \@ "c").toInt, (node \@ "d").toInt)
          }
          contingencyTables.size should (be > 0 and be <= 100)
          forAll(contingencyTables) { ct =>
            ct.support.value should be >= 0.001
            ct.confidence.value should be >= 0.5
          }
        }
        code
      }
    }
    code shouldBe 303
  }

  it should "pick up completed result" in {
    taskId should not be empty
    authorizedRequest(Get("/api/v1/complete-result/" + taskId.get)) ~> route ~> check {
      response.status.intValue shouldBe 200
      val contingencyTables = (responseAs[NodeSeq] \\ "FourFtTable").map { node =>
        ContingencyTable((node \@ "a").toInt, (node \@ "b").toInt, (node \@ "c").toInt, (node \@ "d").toInt)
      }
      contingencyTables.size shouldBe 13
    }
  }

  it should "mine without antecedent and consequent and return max rules" in {
    import MysqlPreprocessingDbOps.datasetAudiology._
    val input = inputPmmlAudiology(datasetDetail, attributes, InterestMeasures(Confidence(0.9), Support(0.01), Limit(10000), MaxRuleLength(8))).map { node =>
      removeXmlElements(node, "AntecedentSetting")
    }
    authorizedRequest(Post("/api/v1/mine", input)) ~> route ~> check {
      response.status.intValue shouldBe 202
      taskId = Some((responseAs[NodeSeq] \\ "task-id").text)
    }
    val code = BasicFunction.repeatUntil[Int]()(code => code != 206 && code != 204) {
      authorizedRequest(Get("/api/v1/partial-result/" + taskId.get)) ~> route ~> check {
        response.status.intValue
      }
    }
    code shouldBe 303
    authorizedRequest(Get("/api/v1/complete-result/" + taskId.get)) ~> route ~> check {
      response.status.intValue shouldBe 200
    }
  }

  "MinerService with Spark" should "start mine with some PMML" taggedAs RestSpec.SlowTest in {
    import HivePreprocessingDbOps.datasetBarbora._
    taskId = None
    authorizedRequest(Post("/api/v1/mine", inputPmmlBarbora(datasetDetail, attributes))) ~> route ~> check {
      response.status.intValue shouldBe 202
      taskId = Some((responseAs[NodeSeq] \\ "task-id").text)
    }
  }

  it should "pick up partial results" taggedAs RestSpec.SlowTest in {
    taskId should not be empty
    val code = BasicFunction.repeatUntil[Int](1 second)(code => code != 206 && code != 204) {
      authorizedRequest(Get("/api/v1/partial-result/" + taskId.get)) ~> route ~> check {
        val code = response.status.intValue
        if (code == 206) {
          val contingencyTables = (responseAs[NodeSeq] \\ "FourFtTable").map { node =>
            ContingencyTable((node \@ "a").toInt, (node \@ "b").toInt, (node \@ "c").toInt, (node \@ "d").toInt)
          }
          contingencyTables.size should (be > 0 and be <= 100)
          forAll(contingencyTables) { ct =>
            ct.support.value should be >= 0.01
            ct.confidence.value should be >= 0.1
          }
        }
        code
      }
    }
    code shouldBe 303
  }

  it should "pick up completed result" taggedAs RestSpec.SlowTest in {
    taskId should not be empty
    authorizedRequest(Get("/api/v1/complete-result/" + taskId.get)) ~> route ~> check {
      response.status.intValue shouldBe 200
      val contingencyTables = (responseAs[NodeSeq] \\ "FourFtTable").map { node =>
        ContingencyTable((node \@ "a").toInt, (node \@ "b").toInt, (node \@ "c").toInt, (node \@ "d").toInt)
      }
      contingencyTables.size shouldBe 2
    }
  }

  "OutlierDetectionService" should "find outliers" in {
    import MysqlPreprocessingDbOps.datasetBarbora._
    val taskId = authorizedRequest(Post("/api/v1/outlier-detection", JsObject("datasetId" -> JsNumber(datasetDetail.id), "minSupport" -> JsNumber(0.01)))) ~> route ~> check {
      response.status.intValue shouldBe 202
      val json = responseAs[JsObject]
      json.fields("taskId").asInstanceOf[JsString].value
    }
    val code = BasicFunction.repeatUntil[Int](1 second)(code => code != 202) {
      authorizedRequest(Get("/api/v1/outlier-detection/" + taskId)) ~> route ~> check {
        val code = response.status.intValue
        if (code == 201) {
          responseAs[JsObject].fields("dataset").asInstanceOf[JsNumber].value.intValue() shouldBe datasetDetail.id
        }
        code
      }
    }
    code shouldBe 201
    val tasks: Seq[Int] = authorizedRequest(Get("/api/v1/outlier-detection/result/" + datasetDetail.id)) ~> route ~> check {
      response.status.intValue shouldBe 200
      responseAs[JsArray].elements.map(_.asJsObject.fields("id").asInstanceOf[JsNumber].value.intValue())
    }
    tasks should not be empty
    for (task <- tasks) {
      val outliers = authorizedRequest(Get("/api/v1/outlier-detection/result/" + datasetDetail.id + "/" + task + "/outliers?offset=0&limit=10")) ~> route ~> check {
        response.status.intValue shouldBe 200
        responseAs[Seq[OutlierWithInstance]]
      }
      outliers.size shouldBe 10
      println(outliers)
      authorizedRequest(Delete("/api/v1/outlier-detection/result/" + datasetDetail.id + "/" + task)) ~> route ~> check {
        response.status.intValue shouldBe 200
      }
    }
    authorizedRequest(Get("/api/v1/outlier-detection/result/" + datasetDetail.id)) ~> route ~> check {
      response.status.intValue shouldBe 200
      responseAs[JsArray].elements shouldBe empty
    }
  }

}

object RestSpec {

  object SlowTest extends Tag("RestSpec.SlowTest")

}