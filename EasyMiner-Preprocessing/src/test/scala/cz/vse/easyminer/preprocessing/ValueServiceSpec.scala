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
    var tested = 0
    for (attributeDetail <- attributes) {
      if (attributeDetail.name.startsWith("age")) {
        tested = tested + 1
        authorizedRequest(Get(s"/api/v1/dataset/${datasetDetail.id}/attribute/${attributeDetail.id}/values?offset=0&limit=10")) ~> route ~> check {
          response.status.intValue shouldBe 200
          responseAs[JsArray].elements.size shouldBe 10
        }
      } else if (attributeDetail.uniqueValuesSize == 4) {
        tested = tested + 1
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
    tested shouldBe 2
  }

  it should "return error if bad input data" in {
    val result = for (attributeDetail <- attributes) yield {
      if (attributeDetail.name.startsWith("age")) {
        authorizedRequest(Get(s"/api/v1/dataset/${datasetDetail.id}/attribute/${attributeDetail.id}/values?offset=0&limit=1001")) ~> route ~> check {
          response.status.intValue shouldBe 400
        }
        authorizedRequest(Get(s"/api/v1/dataset/${datasetDetail.id}/attribute/${attributeDetail.id}/values?offset=-1&limit=10")) ~> route ~> check {
          response.status.intValue shouldBe 400
        }
        1
      } else {
        0
      }
    }
    result.sum shouldBe 1
  }

}