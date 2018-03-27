package cz.vse.easyminer.preprocessing

import java.util.Date

import cz.vse.easyminer.data.{ExclusiveIntervalBorder, InclusiveIntervalBorder}
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
    attributes should contain only(SimpleAttribute("City", 3, Nil), SimpleAttribute("Dist", 4, Nil))
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
    new PmmlTaskParser(pmml).attributes should contain only SimpleAttribute("", 0, Nil)
    pmml =
    <TransformationDictionary>
      <DerivedField name="City" optype="categorical" dataType="string">
        <MapValues outputColumn="field">
          <FieldColumnPair field="aaa" column="column" />
        </MapValues>
      </DerivedField>
    </TransformationDictionary>
    new PmmlTaskParser(pmml).attributes should contain only SimpleAttribute("City", 0, Nil)
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
    val datasetDetail = DatasetDetail(1, "test-dataset", 1, LimitedDatasetType, 10, new Date, new Date, true)
    val pmml =
    <TransformationDictionary>
      <DerivedField name="City" optype="categorical" dataType="string">
        <MapValues outputColumn="field">
          <FieldColumnPair field="3" column="column" />
        </MapValues>
      </DerivedField>
    </TransformationDictionary>
    val attributes = new PmmlTaskParser(pmml).attributes
    implicit val dbConnectors = new PreprocessingDBConnectors(mysqlUserDatabase, Some(hiveUserDatabase))
    for (attribute <- attributes) {
      datasetDetail.`type`.toAttributeBuilder(datasetDetail, attribute) shouldBe a[AttributeBuilder[_]]
    }
  }

  it should "extract nominal enumeration pmml" in {
    val pmml =
    <TransformationDictionary>
      <DerivedField name="City" optype="categorical" dataType="string">
        <MapValues outputColumn="field">
          <FieldColumnPair field="3" column="column" />
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
    val attributes = new PmmlTaskParser(pmml).attributes.collect {
      case x: NominalEnumerationAttribute => x
    }
    attributes.size shouldBe 1
    val attribute = attributes.head
    attribute.name shouldBe "City"
    attribute.field shouldBe 3
    attribute.bins.size shouldBe 2
    attribute.bins.map { bin =>
      if (bin.name == "good") {
        bin.values should contain only("A", "B")
        1
      } else if (bin.name == "bad") {
        bin.values should contain only("C", "D")
        1
      } else {
        0
      }
    }.sum shouldBe 2
  }

  it should "extract interval enumeration pmml" in {
    val pmml =
    <TransformationDictionary>
      <DerivedField name="age" optype="categorical" dataType="string">
        <Discretize field="1">
            <DiscretizeBin binValue="youngAndOld">
                <Interval closure="closedOpen" rightMargin="18" />
            </DiscretizeBin>
            <DiscretizeBin binValue="youngAndOld">
                <Interval closure="openOpen" leftMargin="70" rightMargin="INF" />
            </DiscretizeBin>
            <DiscretizeBin binValue="adult">
                <Interval closure="closedClosed" leftMargin="18" rightMargin="70"></Interval>
            </DiscretizeBin>
        </Discretize>
      </DerivedField>
    </TransformationDictionary>
    val attributes = new PmmlTaskParser(pmml).attributes.collect {
      case x: NumericIntervalsAttribute => x
    }
    attributes.size shouldBe 1
    val attribute = attributes.head
    attribute.name shouldBe "age"
    attribute.field shouldBe 1
    attribute.bins.size shouldBe 2
    attribute.bins.collect {
      case NumericIntervalsAttribute.Bin("youngAndOld", intervals) =>
        intervals should contain only(NumericIntervalsAttribute.Interval(InclusiveIntervalBorder(Double.NegativeInfinity), ExclusiveIntervalBorder(18)), NumericIntervalsAttribute.Interval(ExclusiveIntervalBorder(70), ExclusiveIntervalBorder(Double.PositiveInfinity)))
        1
      case NumericIntervalsAttribute.Bin("adult", intervals) =>
        intervals should contain only NumericIntervalsAttribute.Interval(InclusiveIntervalBorder(18), InclusiveIntervalBorder(70))
        1
    }.sum shouldBe 2
  }

  it should "extract equidistant intervals" in {
    val pmml =
    <TransformationDictionary>
      <DerivedField name="age" optype="categorical" dataType="string">
        <Discretize field="1">
          <Extension name="algorithm" value="equidistant-intervals" />
          <Extension name="bins" value="5" />
        </Discretize>
      </DerivedField>
    </TransformationDictionary>
    val attributes = new PmmlTaskParser(pmml).attributes.collect {
      case x: EquidistantIntervalsAttribute => x
    }
    attributes.size shouldBe 1
    val attribute = attributes.head
    attribute shouldBe EquidistantIntervalsAttribute("age", 1, 5, Nil)
  }

  it should "extract equifrequent intervals" in {
    val pmml =
    <TransformationDictionary>
      <DerivedField name="age" optype="categorical" dataType="string">
        <Discretize field="1">
          <Extension name="algorithm" value="equifrequent-intervals" />
          <Extension name="bins" value="5" />
        </Discretize>
      </DerivedField>
    </TransformationDictionary>
    val attributes = new PmmlTaskParser(pmml).attributes.collect {
      case x: EquifrequentIntervalsAttribute => x
    }
    attributes.size shouldBe 1
    val attribute = attributes.head
    attribute shouldBe EquifrequentIntervalsAttribute("age", 1, 5, Nil)
  }

  it should "extract equisized intervals" in {
    val pmml =
    <TransformationDictionary>
      <DerivedField name="age" optype="categorical" dataType="string">
        <Discretize field="1">
          <Extension name="algorithm" value="equisized-intervals" />
          <Extension name="support" value="0.5" />
        </Discretize>
      </DerivedField>
    </TransformationDictionary>
    val attributes = new PmmlTaskParser(pmml).attributes.collect {
      case x: EquisizedIntervalsAttribute => x
    }
    attributes.size shouldBe 1
    val attribute = attributes.head
    attribute shouldBe EquisizedIntervalsAttribute("age", 1, 0.5, Nil)
  }

  it should "extract attribute features" in {
    val pmml =
    <TransformationDictionary>
      <DerivedField name="age" optype="categorical" dataType="string">
        <Extension name="preserveUncovered" value="true" />
        <Discretize field="1">
          <Extension name="algorithm" value="equisized-intervals" />
          <Extension name="support" value="0.5" />
          <Extension name="leftMarginOpen" value="20" />
          <Extension name="rightMarginClose" value="30" />
        </Discretize>
      </DerivedField>
    </TransformationDictionary>
    val attributes = new PmmlTaskParser(pmml).attributes.collect {
      case x: EquisizedIntervalsAttribute => x
    }
    attributes.size shouldBe 1
    val attribute = attributes.head
    attribute should matchPattern { case EquisizedIntervalsAttribute("age", 1, 0.5, _) => }
    attribute.features should contain allOf(IntervalsBorder(Some(ExclusiveIntervalBorder(20)), Some(InclusiveIntervalBorder(30))), PreserveUncovered)
  }

}
