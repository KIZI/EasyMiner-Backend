package cz.vse.easyminer.preprocessing

import cz.vse.easyminer.data.impl.db.mysql.MysqlFieldOps
import cz.vse.easyminer.preprocessing.impl.JsonFormatters
import org.scalatest.{DoNotDiscover, FlatSpec, Matchers}
import spray.http.FormData
import spray.httpx.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, JsArray}

/**
  * Created by propan on 26. 9. 2015.
  */
@DoNotDiscover
class AttributeServiceSpec(restSpec: RestSpec, datasetServiceSpec: DatasetServiceSpec) extends FlatSpec with Matchers {

  import DefaultJsonProtocol._
  import JsonFormatters.JsonAttributeDetail._
  import JsonFormatters.JsonDatasetDetail._
  import SprayJsonSupport._
  import restSpec._

  lazy val fields = MysqlFieldOps(datasetServiceSpec.dataSourceDetail)(DBSpec.makeMysqlConnector(false)).getAllFields

  lazy val datasetDetail = testTask(Post("/api/v1/dataset", FormData(Map("dataSource" -> datasetServiceSpec.dataSourceDetail.id.toString, "name" -> "test-mysql-dataset")))) {
    responseAs[DatasetDetail]
  }.get

  "AttributeService" should "return empty attribute list" in {
    datasetDetail.size shouldBe 6181
    authorizedRequest(Get(s"/api/v1/dataset/${datasetDetail.id}/attribute")) ~> route ~> check {
      response.status.intValue shouldBe 200
      responseAs[JsArray].elements.size shouldBe 0
    }
  }

  it should "create a simple attribute" in {
    for (field <- fields) {
      val attributeName = field.name + "-attr"
      val pmml =
      <TransformationDictionary>
        <DerivedField name={attributeName} optype="categorical" dataType="string">
          <MapValues outputColumn="field">
            <FieldColumnPair field={field.id.toString} column="column" />
          </MapValues>
        </DerivedField>
      </TransformationDictionary>
      testTask(Post(s"/api/v1/dataset/${datasetDetail.id}/attribute", pmml)) {
        response.status.intValue shouldBe 200
        val attributeDetailList = responseAs[List[AttributeDetail]]
        attributeDetailList.size shouldBe 1
        for (attributeDetail <- attributeDetailList) {
          attributeDetail.field shouldBe field.id
          attributeDetail.name shouldBe attributeName
          attributeDetail.uniqueValuesSize shouldBe field.uniqueValuesSize
        }
      }
    }
    var pmml =
    <TransformationDictionary>{
      fields.take(2).map { field =>
        val attributeName = field.name + "-attr"
        <DerivedField name={attributeName} optype="categorical" dataType="string">
          <MapValues outputColumn="field">
            <FieldColumnPair field={field.id.toString} column="column" />
          </MapValues>
        </DerivedField>
      }
      }</TransformationDictionary>
    testTask(Post(s"/api/v1/dataset/${datasetDetail.id}/attribute", pmml)) {
      response.status.intValue shouldBe 200
      responseAs[JsArray].elements.size shouldBe 2
    }
    pmml =
    <TransformationDictionary>
        <DerivedField name="aaa" optype="categorical" dataType="string">
          <MapValues outputColumn="field">
            <FieldColumnPair field="0" column="column" />
          </MapValues>
        </DerivedField>
    </TransformationDictionary>
    testTask(Post(s"/api/v1/dataset/${datasetDetail.id}/attribute", pmml), expectedStatusCode = 400) {
      response.status.intValue shouldBe 404
    }
    pmml =
    <TransformationDictionary>
        <DerivedField name="" optype="categorical" dataType="string">
          <MapValues outputColumn="field">
            <FieldColumnPair field={fields.head.id.toString} column="column" />
          </MapValues>
        </DerivedField>
    </TransformationDictionary>
    testTask(Post(s"/api/v1/dataset/${datasetDetail.id}/attribute", pmml), expectedStatusCode = 400) {
      response.status.intValue shouldBe 404
    }
  }

  it should "return information about attributes" in {
    val attributeDetailList = authorizedRequest(Get(s"/api/v1/dataset/${datasetDetail.id}/attribute")) ~> route ~> check {
      response.status.intValue shouldBe 200
      val attributeDetailList = responseAs[List[AttributeDetail]]
      attributeDetailList.size shouldBe 5
      attributeDetailList
    }
    for (attributeDetail <- attributeDetailList) {
      authorizedRequest(Get(s"/api/v1/dataset/${datasetDetail.id}/attribute/${attributeDetail.id}")) ~> route ~> check {
        response.status.intValue shouldBe 200
        responseAs[AttributeDetail] shouldBe attributeDetail
      }
    }
    for (attributeDetail <- attributeDetailList.sortBy(_.id).takeRight(2)) {
      authorizedRequest(Put(s"/api/v1/dataset/${datasetDetail.id}/attribute/${attributeDetail.id}", "new-name")) ~> route ~> check {
        response.status.intValue shouldBe 200
      }
      authorizedRequest(Put(s"/api/v1/dataset/${datasetDetail.id}/attribute/${attributeDetail.id}", "")) ~> route ~> check {
        response.status.intValue shouldBe 400
      }
      authorizedRequest(Get(s"/api/v1/dataset/${datasetDetail.id}/attribute/${attributeDetail.id}")) ~> route ~> check {
        response.status.intValue shouldBe 200
        responseAs[AttributeDetail].name shouldBe "new-name"
      }
      authorizedRequest(Delete(s"/api/v1/dataset/${datasetDetail.id}/attribute/${attributeDetail.id}")) ~> route ~> check {
        response.status.intValue shouldBe 200
      }
    }
    authorizedRequest(Get(s"/api/v1/dataset/${datasetDetail.id}/attribute")) ~> route ~> check {
      response.status.intValue shouldBe 200
      responseAs[JsArray].elements.size shouldBe 3
    }
  }

}