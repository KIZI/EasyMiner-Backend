package cz.vse.easyminer.data

import cz.vse.easyminer.data.impl.{JsonFormatters, Validators}
import org.scalatest.{DoNotDiscover, FlatSpec, Matchers}
import spray.httpx.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, JsArray}

/**
 * Created by propan on 26. 9. 2015.
 */
@DoNotDiscover
class FieldServiceSpec(restSpec: RestSpec) extends FlatSpec with Matchers {

  import DefaultJsonProtocol._
  import JsonFormatters.JsonFieldDetail._
  import SprayJsonSupport._
  import restSpec._

  "FieldService" should "return fields for a specific datasource" in {
    authorizedRequest(Get("/api/v1/datasource/1/field")) ~> route ~> check {
      response.status.intValue shouldBe 200
      responseAs[JsArray].elements.size shouldBe 7
    }
    authorizedRequest(Get("/api/v1/datasource/1/field/3")) ~> route ~> check {
      response.status.intValue shouldBe 200
      responseAs[FieldDetail] shouldBe FieldDetail(3, 1, "district", NominalFieldType, 79)
    }
  }

  it should "rename some field" in {
    authorizedRequest(Put("/api/v1/datasource/1/field/3", "newname")) ~> route ~> check {
      response.status.intValue shouldBe 200
    }
    authorizedRequest(Get("/api/v1/datasource/1/field/3")) ~> route ~> check {
      response.status.intValue shouldBe 200
      responseAs[FieldDetail].name shouldBe "newname"
    }
  }

  it should "delete some field" in {
    authorizedRequest(Delete("/api/v1/datasource/1/field/3")) ~> route ~> check {
      response.status.intValue shouldBe 200
    }
    authorizedRequest(Get("/api/v1/datasource/1/field/3")) ~> route ~> check {
      response.status.intValue shouldBe 404
    }
  }

  it should "return some error if bad request inputs" in {
    authorizedRequest(Put("/api/v1/datasource/1/field/1", Array.fill(Validators.tableColMaxlen + 1)('a').mkString)) ~> route ~> check {
      response.status.intValue shouldBe 400
    }
  }

}
