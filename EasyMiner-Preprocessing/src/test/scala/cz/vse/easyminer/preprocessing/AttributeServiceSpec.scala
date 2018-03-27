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

  it should "create nominal enumeration attribute" in {
    val ratingField = fields.find(_.name == "rating").get
    val pmml =
      <TransformationDictionary>
        <DerivedField name={ratingField.name + "-aa"} optype="categorical" dataType="string">
          <MapValues outputColumn="field">
            <FieldColumnPair field={ratingField.id.toString} column="column" />
              <InlineTable>
                <row>
                    <column>A</column>
                    <field>good</field>
                </row>
                <row>
                    <column>B</column>
                    <field>good</field>
                </row>
                <row>
                    <column>C</column>
                    <field>bad</field>
                </row>
                <row>
                    <column>D</column>
                    <field>bad</field>
                </row>
            </InlineTable>
          </MapValues>
        </DerivedField>
      </TransformationDictionary>
    val attributeId = testTask(Post(s"/api/v1/dataset/${datasetDetail.id}/attribute", pmml)) {
      response.status.intValue shouldBe 200
      val attributeDetailList = responseAs[List[AttributeDetail]]
      attributeDetailList.size shouldBe 1
      for (attributeDetail <- attributeDetailList) {
        attributeDetail.field shouldBe ratingField.id
        attributeDetail.name shouldBe ratingField.name + "-aa"
        attributeDetail.uniqueValuesSize shouldBe 2
      }
      attributeDetailList.head.id
    }
    attributeId should not be empty
    for (id <- attributeId) {
      authorizedRequest(Delete(s"/api/v1/dataset/${datasetDetail.id}/attribute/$id")) ~> route ~> check {
        response.status.intValue shouldBe 200
      }
    }
  }

  it should "create interval enumeration attribute" in {
    val ageField = fields.find(_.name == "age").get
    val pmml =
      <TransformationDictionary>
        <DerivedField name={ageField.name + "-aa"} optype="categorical" dataType="string">
          <Discretize field={ageField.id.toString}>
            <DiscretizeBin binValue="youngAndOld">
                <Interval closure="closedOpen" rightMargin="30" />
            </DiscretizeBin>
            <DiscretizeBin binValue="youngAndOld">
                <Interval closure="openOpen" leftMargin="50" rightMargin="INF" />
            </DiscretizeBin>
            <DiscretizeBin binValue="adult">
                <Interval closure="closedClosed" leftMargin="30" rightMargin="50"></Interval>
            </DiscretizeBin>
          </Discretize>
        </DerivedField>
      </TransformationDictionary>
    val attributeId = testTask(Post(s"/api/v1/dataset/${datasetDetail.id}/attribute", pmml)) {
      response.status.intValue shouldBe 200
      val attributeDetailList = responseAs[List[AttributeDetail]]
      attributeDetailList.size shouldBe 1
      for (attributeDetail <- attributeDetailList) {
        attributeDetail.field shouldBe ageField.id
        attributeDetail.name shouldBe ageField.name + "-aa"
        attributeDetail.uniqueValuesSize shouldBe 2
      }
      attributeDetailList.head.id
    }
    attributeId should not be empty
    for (id <- attributeId) {
      authorizedRequest(Delete(s"/api/v1/dataset/${datasetDetail.id}/attribute/$id")) ~> route ~> check {
        response.status.intValue shouldBe 200
      }
    }
  }

  it should "create equidistant intervals attribute" in {
    val ageField = fields.find(_.name == "age").get
    val pmml =
      <TransformationDictionary>
        <DerivedField name={ageField.name + "-aa"} optype="categorical" dataType="string">
          <Discretize field={ageField.id.toString}>
            <Extension name="algorithm" value="equidistant-intervals" />
            <Extension name="bins" value="5" />
          </Discretize>
        </DerivedField>
      </TransformationDictionary>
    val attributeId = testTask(Post(s"/api/v1/dataset/${datasetDetail.id}/attribute", pmml)) {
      response.status.intValue shouldBe 200
      val attributeDetailList = responseAs[List[AttributeDetail]]
      attributeDetailList.size shouldBe 1
      for (attributeDetail <- attributeDetailList) {
        attributeDetail.field shouldBe ageField.id
        attributeDetail.name shouldBe ageField.name + "-aa"
        attributeDetail.uniqueValuesSize shouldBe 5
      }
      attributeDetailList.head.id
    }
    attributeId should not be empty
    for (id <- attributeId) {
      authorizedRequest(Delete(s"/api/v1/dataset/${datasetDetail.id}/attribute/$id")) ~> route ~> check {
        response.status.intValue shouldBe 200
      }
    }
  }

  it should "create equifrequent intervals attribute" in {
    val ageField = fields.find(_.name == "age").get
    val pmml =
      <TransformationDictionary>
        <DerivedField name={ageField.name + "-aa"} optype="categorical" dataType="string">
          <Discretize field={ageField.id.toString}>
            <Extension name="algorithm" value="equifrequent-intervals" />
            <Extension name="bins" value="5" />
          </Discretize>
        </DerivedField>
      </TransformationDictionary>
    val attributeId = testTask(Post(s"/api/v1/dataset/${datasetDetail.id}/attribute", pmml)) {
      response.status.intValue shouldBe 200
      val attributeDetailList = responseAs[List[AttributeDetail]]
      attributeDetailList.size shouldBe 1
      for (attributeDetail <- attributeDetailList) {
        attributeDetail.field shouldBe ageField.id
        attributeDetail.name shouldBe ageField.name + "-aa"
        attributeDetail.uniqueValuesSize shouldBe 5
      }
      attributeDetailList.head.id
    }
    attributeId should not be empty
    for (id <- attributeId) {
      authorizedRequest(Delete(s"/api/v1/dataset/${datasetDetail.id}/attribute/$id")) ~> route ~> check {
        response.status.intValue shouldBe 200
      }
    }
  }

  it should "create equisized intervals attribute" in {
    val ageField = fields.find(_.name == "age").get
    val pmml =
      <TransformationDictionary>
        <DerivedField name={ageField.name + "-aa"} optype="categorical" dataType="string">
          <Discretize field={ageField.id.toString}>
            <Extension name="algorithm" value="equisized-intervals" />
            <Extension name="support" value="0.2" />
          </Discretize>
        </DerivedField>
      </TransformationDictionary>
    val attributeId = testTask(Post(s"/api/v1/dataset/${datasetDetail.id}/attribute", pmml)) {
      response.status.intValue shouldBe 200
      val attributeDetailList = responseAs[List[AttributeDetail]]
      attributeDetailList.size shouldBe 1
      for (attributeDetail <- attributeDetailList) {
        attributeDetail.field shouldBe ageField.id
        attributeDetail.name shouldBe ageField.name + "-aa"
        attributeDetail.uniqueValuesSize shouldBe 4
      }
      attributeDetailList.head.id
    }
    attributeId should not be empty
    for (id <- attributeId) {
      authorizedRequest(Delete(s"/api/v1/dataset/${datasetDetail.id}/attribute/$id")) ~> route ~> check {
        response.status.intValue shouldBe 200
      }
    }
  }

  it should "return information about attributes" in {
    val attributeDetailList = authorizedRequest(Get(s"/api/v1/dataset/${datasetDetail.id}/attribute")) ~> route ~> check {
      response.status.intValue shouldBe 200
      val attributeDetailList = responseAs[List[AttributeDetail]]
      attributeDetailList.size shouldBe 6
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
      responseAs[JsArray].elements.size shouldBe 4
    }
  }

}