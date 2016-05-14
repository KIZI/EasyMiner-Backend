package cz.vse.easyminer.data

import cz.vse.easyminer.data.impl.JsonFormatters
import org.scalatest.{DoNotDiscover, FlatSpec, Matchers}
import spray.httpx.SprayJsonSupport
import spray.json._

/**
 * Created by propan on 25. 9. 2015.
 */
@DoNotDiscover
class ValueServiceSpec(restSpec: RestSpec) extends FlatSpec with Matchers {

  import DefaultJsonProtocol._
  import JsonFormatters.JsonDataSourceDetail._
  import JsonFormatters.JsonFieldDetail._
  import SprayJsonSupport._
  import restSpec._

  "ValueService" should "return values for some fields" in {
    authorizedRequest(Get("/api/v1/datasource/1/field/3/values?offset=0&limit=10")) ~> route ~> check {
      response.status.intValue shouldBe 200
      responseAs[JsArray].elements.size shouldBe 10
    }
    authorizedRequest(Get("/api/v1/datasource/1/field/1/stats")) ~> route ~> check {
      response.status.intValue shouldBe 200
      val stats = responseAs[JsObject]
      stats.fields.get("min") shouldBe Some(JsNumber(20.0))
      stats.fields.get("max") shouldBe Some(JsNumber(65.0))
    }
    testTask(Get("/api/v1/datasource/1/field/1/aggregated-values?bins=10")) {
      response.status.intValue shouldBe 200
      val bins = responseAs[JsArray]
      bins.elements.size shouldBe 11
      val min = bins.elements.tail.head.asJsObject.fields
      val max = bins.elements.last.asJsObject.fields
      min.get("from") shouldBe Some(JsNumber(20.0))
      min.get("frequency") shouldBe Some(JsNumber(657))
      min.get("fromInclusive") shouldBe Some(JsBoolean(true))
      max.get("to") shouldBe Some(JsNumber(65.0))
      max.get("frequency") shouldBe Some(JsNumber(469))
      max.get("toInclusive") shouldBe Some(JsBoolean(true))
    }
    testTask(Get("/api/v1/datasource/1/field/1/aggregated-values?bins=10&min=25&max=50&minInclusive=false&maxInclusive=false")) {
      response.status.intValue shouldBe 200
      val bins = responseAs[JsArray]
      bins.elements.size shouldBe 11
      val min = bins.elements.tail.head.asJsObject.fields
      val max = bins.elements.last.asJsObject.fields
      min.get("from") shouldBe Some(JsNumber(25.0))
      min.get("fromInclusive") shouldBe Some(JsBoolean(false))
      min.get("frequency") shouldBe Some(JsNumber(225))
      max.get("to") shouldBe Some(JsNumber(50.0))
      max.get("toInclusive") shouldBe Some(JsBoolean(false))
      max.get("frequency") shouldBe Some(JsNumber(298))
    }
    testTask(Get("/api/v1/datasource/1/field/1/aggregated-values?bins=2&min=100&max=200")) {
      response.status.intValue shouldBe 200
      val bins = responseAs[JsArray]
      bins.elements.size shouldBe 3
      val min = bins.elements.tail.head.asJsObject.fields
      val max = bins.elements.last.asJsObject.fields
      min.get("from") shouldBe Some(JsNumber(100.0))
      min.get("frequency") shouldBe Some(JsNumber(0))
      max.get("to") shouldBe Some(JsNumber(200.0))
      max.get("frequency") shouldBe Some(JsNumber(0))
    }
  }

  it should "work with hive database" taggedAs(RestSpec.HiveTest, RestSpec.SlowTest) in {
    val hiveDataSourceId = authorizedRequest(Get("/api/v1/datasource")) ~> route ~> check {
      response.status.intValue shouldBe 200
      responseAs[List[DataSourceDetail]].find(_.`type` == UnlimitedDataSourceType).map(_.id).getOrElse(0)
    }
    val hiveFieldsMap = authorizedRequest(Get(s"/api/v1/datasource/$hiveDataSourceId/field")) ~> route ~> check {
      response.status.intValue shouldBe 200
      responseAs[List[FieldDetail]].map(x => x.name -> x.id).toMap
    }
    def hiveFieldId(name: String) = hiveFieldsMap.getOrElse(name, 0)
    authorizedRequest(Get(s"/api/v1/datasource/$hiveDataSourceId/field/${hiveFieldId("age")}/values?offset=0&limit=10")) ~> route ~> check {
      response.status.intValue shouldBe 200
      responseAs[JsArray].elements.size shouldBe 10
    }
    authorizedRequest(Get(s"/api/v1/datasource/$hiveDataSourceId/field/${hiveFieldId("rating")}/values?offset=0&limit=10")) ~> route ~> check {
      response.status.intValue shouldBe 200
      val values = responseAs[JsArray].elements
      values.size shouldBe 4
      for (substring <- List("\"value\":\"A\"", "\"value\":\"B\"", "\"value\":\"C\"", "\"value\":\"D\"")) {
        values.toString() should include(substring)
      }
    }
  }

  it should "return error if bad input data" in {
    authorizedRequest(Get("/api/v1/datasource/1/field/3/values?offset=0&limit=1001")) ~> route ~> check {
      response.status.intValue shouldBe 400
    }
    authorizedRequest(Get("/api/v1/datasource/1/field/3/values?offset=-1&limit=10")) ~> route ~> check {
      response.status.intValue shouldBe 400
    }
    authorizedRequest(Get("/api/v1/datasource/1/field/3/stats")) ~> route ~> check {
      response.status.intValue shouldBe 404
    }
    authorizedRequest(Get("/api/v1/datasource/1/field/3/aggregated-values?bins=5")) ~> route ~> check {
      response.status.intValue shouldBe 404
    }
    testTask(Get("/api/v1/datasource/1/field/1/aggregated-values?bins=1001"), expectedStatusCode = 400) {
      response.status.intValue shouldBe 404
    }
    testTask(Get("/api/v1/datasource/1/field/1/aggregated-values?bins=10&min=10&max=10"), expectedStatusCode = 400) {
      response.status.intValue shouldBe 404
    }
  }

}
