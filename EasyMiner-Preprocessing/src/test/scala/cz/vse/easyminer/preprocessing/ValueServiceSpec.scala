package cz.vse.easyminer.preprocessing

import cz.vse.easyminer.preprocessing.impl.JsonFormatters
import org.scalatest.{DoNotDiscover, FlatSpec, Matchers}
import spray.httpx.SprayJsonSupport
import spray.json._

/**
 * Created by propan on 26. 9. 2015.
 */
@DoNotDiscover
class ValueServiceSpec(restSpec: RestSpec) extends FlatSpec with Matchers {

  import DefaultJsonProtocol._
  import JsonFormatters.JsonAttributeDetail._
  import JsonFormatters.JsonDatasetDetail._
  import SprayJsonSupport._
  import restSpec._

  lazy val datasetDetail = authorizedRequest(Get("/api/v1/dataset")) ~> route ~> check {
    responseAs[List[DatasetDetail]].head
  }

  lazy val attributes = authorizedRequest(Get(s"/api/v1/dataset/${datasetDetail.id}/attribute")) ~> route ~> check {
    responseAs[List[AttributeDetail]]
  }

  "ValueService" should "return values for some attributes" in {
    for (attributeDetail <- attributes) {
      if (attributeDetail.`type` == NumericAttributeType) {
        authorizedRequest(Get(s"/api/v1/dataset/${datasetDetail.id}/attribute/${attributeDetail.id}/values?offset=0&limit=10")) ~> route ~> check {
          response.status.intValue shouldBe 200
          responseAs[JsArray].elements.size shouldBe 10
        }
        authorizedRequest(Get(s"/api/v1/dataset/${datasetDetail.id}/attribute/${attributeDetail.id}/stats")) ~> route ~> check {
          response.status.intValue shouldBe 200
          val stats = responseAs[JsObject]
          stats.fields.get("min") shouldBe Some(JsNumber(20.0))
          stats.fields.get("max") shouldBe Some(JsNumber(65.0))
        }
        testTask(Get(s"/api/v1/dataset/${datasetDetail.id}/attribute/${attributeDetail.id}/aggregated-values?bins=10")) {
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
      } else if (attributeDetail.uniqueValuesSize == 4) {
        authorizedRequest(Get(s"/api/v1/dataset/${datasetDetail.id}/attribute/${attributeDetail.id}/values?offset=0&limit=10")) ~> route ~> check {
          response.status.intValue shouldBe 200
          val elements = responseAs[JsArray].elements
          elements.size shouldBe 4
          elements.collect {
            case JsObject(x) if x.contains("value") => x("value")
          } collect {
            case JsString(x) => x
          } should contain allOf("A", "B", "C", "D")
        }
      }
    }
  }

  it should "return error if bad input data" in {
    for (attributeDetail <- attributes) {
      if (attributeDetail.`type` == NumericAttributeType) {
        authorizedRequest(Get(s"/api/v1/dataset/${datasetDetail.id}/attribute/${attributeDetail.id}/values?offset=0&limit=1001")) ~> route ~> check {
          response.status.intValue shouldBe 400
        }
        authorizedRequest(Get(s"/api/v1/dataset/${datasetDetail.id}/attribute/${attributeDetail.id}/values?offset=-1&limit=10")) ~> route ~> check {
          response.status.intValue shouldBe 400
        }
        List(
          s"/api/v1/dataset/${datasetDetail.id}/attribute/${attributeDetail.id}/aggregated-values?bins=1001",
          s"/api/v1/dataset/${datasetDetail.id}/attribute/${attributeDetail.id}/aggregated-values?bins=10&min=10&max=10"
        ) foreach { histogramUri =>
          testTask(Get(histogramUri), expectedStatusCode = 400) {
            response.status.intValue shouldBe 404
          }
        }
      } else {
        authorizedRequest(Get(s"/api/v1/dataset/${datasetDetail.id}/attribute/${attributeDetail.id}/stats")) ~> route ~> check {
          response.status.intValue shouldBe 404
        }
        authorizedRequest(Get(s"/api/v1/dataset/${datasetDetail.id}/attribute/${attributeDetail.id}/aggregated-values?bins=5")) ~> route ~> check {
          response.status.intValue shouldBe 404
        }
      }
    }
  }

}