package cz.vse.easyminer.preprocessing

import cz.vse.easyminer.preprocessing.impl.PreprocessingDBConnectors
import cz.vse.easyminer.preprocessing.impl.parser.PmmlTaskParser
import org.scalatest.{FlatSpec, Matchers}

/**
 * Created by propan on 2. 2. 2016.
 */
class TaskParserSpec extends FlatSpec with Matchers with ConfOpt {

  import cz.vse.easyminer.preprocessing.impl.db.DatasetTypeConversions._

  "PmmlTaskParser" should "parse a pmmml task with simple attribute" in {
    val pmml =
    <TransformationDictionary>
      <DerivedField name="City" optype="categorical" dataType="string">
        <MapValues outputColumn="field">
          <FieldColumnPair field="3" column="column" />
        </MapValues>
      </DerivedField>
    </TransformationDictionary>
    val attributes = new PmmlTaskParser(pmml).attributes
    attributes.size shouldBe 1
    for (attribute <- attributes) {
      attribute shouldBe a[SimpleAttribute]
      attribute.field shouldBe 3
      attribute.name shouldBe "City"
    }
  }

  it should "parser a pmml task with several attributes" in {
    val pmml =
    <TransformationDictionary>
      <DerivedField name="City" optype="categorical" dataType="string">
        <MapValues outputColumn="field">
          <FieldColumnPair field="3" column="column" />
        </MapValues>
      </DerivedField>
      <DerivedField name="Dist" optype="categorical" dataType="string">
        <MapValues outputColumn="field">
          <FieldColumnPair field="4" column="column" />
        </MapValues>
      </DerivedField>
    </TransformationDictionary>
    val attributes = new PmmlTaskParser(pmml).attributes
    attributes.size shouldBe 2
    attributes should contain only(SimpleAttribute("City", 3), SimpleAttribute("Dist", 4))
  }

  it should "return an empty seq or default values if invalid data" in {
    var pmml =
    <TransformationDictionary>
      <DerivedField optype="categorical" dataType="string">
        <MapValues outputColumn="field">
          <FieldColumnPair column="column" />
        </MapValues>
      </DerivedField>
    </TransformationDictionary>
    new PmmlTaskParser(pmml).attributes should contain only SimpleAttribute("", 0)
    pmml =
    <TransformationDictionary>
      <DerivedField name="City" optype="categorical" dataType="string">
        <MapValues outputColumn="field">
          <FieldColumnPair field="aaa" column="column" />
        </MapValues>
      </DerivedField>
    </TransformationDictionary>
    new PmmlTaskParser(pmml).attributes should contain only SimpleAttribute("City", 0)
    pmml =
    <TransformationDictionary>
      <DerivedField name="City" optype="categorical" dataType="string">
        <MapValues outputColumn="field">
          <FieldColumnPair field="3" column="column">
            <SomeChild></SomeChild>
          </FieldColumnPair>
        </MapValues>
      </DerivedField>
    </TransformationDictionary>
    new PmmlTaskParser(pmml).attributes shouldBe empty
  }

  it should "be transformable to a attribute builder" in {
    val datasetDetail = DatasetDetail(1, "test-dataset", 1, LimitedDatasetType, 10, true)
    val pmml =
    <TransformationDictionary>
      <DerivedField name="City" optype="categorical" dataType="string">
        <MapValues outputColumn="field">
          <FieldColumnPair field="3" column="column" />
        </MapValues>
      </DerivedField>
    </TransformationDictionary>
    val attributes = new PmmlTaskParser(pmml).attributes
    implicit val dbConnectors = new PreprocessingDBConnectors(mysqlUserDatabase, hiveUserDatabase)
    for (attribute <- attributes) {
      datasetDetail.`type`.toAttributeBuilder(datasetDetail, attribute) shouldBe a[AttributeBuilder]
    }
  }

}
