package cz.vse.easyminer.preprocessing

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.preprocessing.impl.JsonFormatters
import org.scalatest.{DoNotDiscover, FlatSpec, Matchers}
import spray.http.FormData
import spray.httpx.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, JsArray}

/**
 * Created by propan on 26. 9. 2015.
 */
@DoNotDiscover
class DatasetServiceSpec(restSpec: RestSpec) extends FlatSpec with Matchers {

  import DefaultJsonProtocol._
  import JsonFormatters.JsonDatasetDetail._
  import SprayJsonSupport._
  import restSpec._

  lazy val dataSourceDetail = new MysqlDataDbOps {
    implicit val mysqlDBConnector: MysqlDBConnector = DBSpec.makeMysqlConnector(false)
  }.buildDatasource("test-mysql", getClass.getResourceAsStream("/test.csv"))

  "DatasetService" should "return empty dataset list" in {
    authorizedRequest(Get("/api/v1/dataset")) ~> route ~> check {
      response.status.intValue shouldBe 200
      responseAs[JsArray].elements.size shouldBe 0
    }
    dataSourceDetail.size shouldBe 6181
  }

  it should "create a new dataset" in {
    testTask(Post("/api/v1/dataset", FormData(Map("dataSource" -> dataSourceDetail.id.toString, "name" -> "test-mysql-dataset")))) {
      response.status.intValue shouldBe 200
      val datasetDetail = responseAs[DatasetDetail]
      datasetDetail.id shouldBe dataSourceDetail.id
      datasetDetail.size shouldBe dataSourceDetail.size
      datasetDetail.name shouldBe "test-mysql-dataset"
    }
  }

  it should "return error if bad input data within dataset creation" in {
    authorizedRequest(Post("/api/v1/dataset")) ~> route ~> check {
      response.status.intValue shouldBe 400
    }
    authorizedRequest(Post("/api/v1/dataset", FormData(Map("dataSource" -> "0", "name" -> "test-mysql-dataset")))) ~> route ~> check {
      response.status.intValue shouldBe 404
    }
    testTask(Post("/api/v1/dataset", FormData(Map("dataSource" -> dataSourceDetail.id.toString, "name" -> ""))), expectedStatusCode = 400) {
      response.status.intValue shouldBe 404
    }
  }

  it should "return information about datasets" in {
    val datasetDetailList = authorizedRequest(Get("/api/v1/dataset")) ~> route ~> check {
      response.status.intValue shouldBe 200
      val datasetDetailList = responseAs[List[DatasetDetail]]
      datasetDetailList.size shouldBe 1
      datasetDetailList
    }
    authorizedRequest(Get("/api/v1/dataset/0")) ~> route ~> check {
      response.status.intValue shouldBe 404
    }
    for (datasetDetail <- datasetDetailList) {
      authorizedRequest(Get("/api/v1/dataset/" + datasetDetail.id)) ~> route ~> check {
        response.status.intValue shouldBe 200
        responseAs[DatasetDetail] shouldBe datasetDetail
      }
      authorizedRequest(Put("/api/v1/dataset/" + datasetDetail.id)) ~> route ~> check {
        response.status.intValue shouldBe 400
      }
      authorizedRequest(Put("/api/v1/dataset/" + datasetDetail.id, "new-dataset-name")) ~> route ~> check {
        response.status.intValue shouldBe 200
      }
      authorizedRequest(Get("/api/v1/dataset/" + datasetDetail.id)) ~> route ~> check {
        response.status.intValue shouldBe 200
        responseAs[DatasetDetail].name shouldBe "new-dataset-name"
      }
      authorizedRequest(Delete("/api/v1/dataset/" + datasetDetail.id)) ~> route ~> check {
        response.status.intValue shouldBe 200
      }
    }
    authorizedRequest(Get("/api/v1/dataset")) ~> route ~> check {
      response.status.intValue shouldBe 200
      responseAs[JsArray].elements.size shouldBe 0
    }
  }

}