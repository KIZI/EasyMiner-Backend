package cz.vse.easyminer.data

import cz.vse.easyminer.data.impl.{JsonFormatters, Validators}
import org.scalatest.{DoNotDiscover, FlatSpec, Matchers}
import spray.httpx.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, JsArray, JsNumber}

/**
  * Created by propan on 26. 9. 2015.
  */
@DoNotDiscover
class DataSourceServiceSpec(restSpec: RestSpec) extends FlatSpec with Matchers {

  import DefaultJsonProtocol._
  import JsonFormatters.JsonDataSourceDetail._
  import SprayJsonSupport._
  import restSpec._

  "DataSourceService" should "return detasources for a specific user" in {
    authorizedRequest(Get("/api/v1/datasource")) ~> route ~> check {
      response.headers.map(x => x.lowercaseName -> x.value) should contain(("access-control-allow-origin", "*"))
      response.status.intValue shouldBe 200
      responseAs[JsArray].elements.size should (be >= 1 or be <= 3)
    }
    authorizedRequest(Get("/api/v1/datasource/1")) ~> route ~> check {
      response.status.intValue shouldBe 200
      responseAs[DataSourceDetail] shouldBe DataSourceDetail(1, "test", LimitedDataSourceType, 6181, true)
    }
  }

  it should "rename datasource field" in {
    authorizedRequest(Put("/api/v1/datasource/1", "newname")) ~> route ~> check {
      response.status.intValue shouldBe 200
    }
    authorizedRequest(Get("/api/v1/datasource/1")) ~> route ~> check {
      response.status.intValue shouldBe 200
      responseAs[DataSourceDetail].name shouldBe "newname"
    }
  }

  it should "return instances" in {
    authorizedRequest(Get("/api/v1/datasource/1/instances?offset=0&limit=10")) ~> route ~> check {
      response.status.intValue shouldBe 200
      val instances = responseAs[JsArray]
      instances.elements.size shouldBe 10
      instances.elements.flatMap(_.asJsObject.fields("values").asInstanceOf[JsArray].elements.map(_.asJsObject.fields("field").asInstanceOf[JsNumber].value)).distinct.size shouldBe 6
    }
  }

  it should "return instances for hive database" taggedAs(RestSpec.HiveTest, RestSpec.SlowTest) in {
    val hiveDataSourceId = authorizedRequest(Get("/api/v1/datasource")) ~> route ~> check {
      response.status.intValue shouldBe 200
      responseAs[List[DataSourceDetail]].find(_.`type` == UnlimitedDataSourceType).map(_.id).getOrElse(0)
    }
    authorizedRequest(Get(s"/api/v1/datasource/$hiveDataSourceId/instances?offset=0&limit=10")) ~> route ~> check {
      response.status.intValue shouldBe 200
      responseAs[JsArray].elements.size shouldBe 10
    }
  }

  it should "return some error if bad request inputs" in {
    authorizedRequest(Put("/api/v1/datasource/1", Array.fill(Validators.tableNameMaxlen + 1)('a').mkString)) ~> route ~> check {
      response.status.intValue shouldBe 400
    }
    authorizedRequest(Get("/api/v1/datasource/1/instances")) ~> route ~> check {
      response.status.intValue shouldBe 404
    }
    authorizedRequest(Get("/api/v1/datasource/1/instances?offset=-1&limit=10")) ~> route ~> check {
      response.status.intValue shouldBe 400
    }
    authorizedRequest(Get("/api/v1/datasource/1/instances?offset=0&limit=1001")) ~> route ~> check {
      response.status.intValue shouldBe 400
    }
    authorizedRequest(Get("/api/v1/datasource/1/instances?offset=0&limit=0")) ~> route ~> check {
      response.status.intValue shouldBe 400
    }
  }

  it should "delete datasources" in {
    authorizedRequest(Delete("/api/v1/datasource/1")) ~> route ~> check {
      response.status.intValue shouldBe 200
    }
    authorizedRequest(Get("/api/v1/datasource/1")) ~> route ~> check {
      response.status.intValue shouldBe 404
    }
  }

  it should "delete hive datasources" taggedAs(RestSpec.HiveTest, RestSpec.SlowTest) in {
    val hiveDataSourceId = authorizedRequest(Get("/api/v1/datasource")) ~> route ~> check {
      response.status.intValue shouldBe 200
      responseAs[List[DataSourceDetail]].find(_.`type` == UnlimitedDataSourceType).map(_.id).getOrElse(0)
    }
    authorizedRequest(Delete(s"/api/v1/datasource/$hiveDataSourceId")) ~> route ~> check {
      response.status.intValue shouldBe 200
    }
    authorizedRequest(Get(s"/api/v1/datasource/$hiveDataSourceId")) ~> route ~> check {
      response.status.intValue shouldBe 404
    }
  }

}
