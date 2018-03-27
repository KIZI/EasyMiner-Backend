package cz.vse.easyminer.preprocessing

import cz.vse.easyminer.core.Validator.ValidationException
import cz.vse.easyminer.core.db._
import cz.vse.easyminer.data.{ExclusiveIntervalBorder, InclusiveIntervalBorder}
import cz.vse.easyminer.preprocessing.impl.db.mysql.{MysqlDatasetBuilder, MysqlSchemaOps}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

import scala.language.implicitConversions

/**
  * Created by propan on 12. 11. 2016.
  */
class DBSpecAttributeBuilders extends FlatSpec with Matchers with TemplateOpt with BeforeAndAfterAll with MysqlDataDbOps {

  import cz.vse.easyminer.data.impl.db.DataSourceTypeConversions.Limited._
  import cz.vse.easyminer.preprocessing.impl.db.DatasetTypeConversions.Limited._

  implicit lazy val mysqlDBConnector: MysqlDBConnector = DBSpec.makeMysqlConnector(false)

  lazy val dataSource = buildDatasource("test", getClass.getResourceAsStream("/test.csv"))

  implicit lazy val dataset = MysqlDatasetBuilder(Dataset("test-prep", dataSource)).build

  lazy val (fieldOps, attributeOps) = (dataSource.toFieldOps(dataSource), dataset.toAttributeOps(dataset))

  "NominalEnumerationAttributeBuiled" should "create attributes" in {
    val (ratingField, districtField) = {
      val fields = fieldOps.getAllFields
      (fields.find(_.name == "rating").get, fields.find(_.name == "district").get)
    }
    intercept[ValidationException] {
      dataset.toAttributeBuilder(dataset).build
    }
    intercept[ValidationException] {
      dataset.toAttributeBuilder(dataset, NominalEnumerationAttribute("rating", ratingField.id, Nil, Nil)).build
    }
    val attributeDetails = dataset.toAttributeBuilder(
      dataset,
      NominalEnumerationAttribute("rating1", ratingField.id, List(NominalEnumerationAttribute.Bin("good", List("A", "B")), NominalEnumerationAttribute.Bin("bad", List("C", "D"))), Nil),
      NominalEnumerationAttribute("rating2", ratingField.id, List(NominalEnumerationAttribute.Bin("good", List("C", "D")), NominalEnumerationAttribute.Bin("bad", List("A", "B"))), Nil),
      NominalEnumerationAttribute("district", districtField.id, List(NominalEnumerationAttribute.Bin("BPT", List("Benesov", "Pardubice", "Trebic")), NominalEnumerationAttribute.Bin("Benesov", List("Olomouc", "Chomutov"))), Nil)
    ).build
    attributeDetails.map(_.name) should contain only("rating1", "rating2", "district")
    attributeDetails.map(_.uniqueValuesSize) should contain only 2
    attributeDetails.find(_.name == "rating1").get.toValueOps.getValues(0, 10).map(_.value) should contain only("good", "bad")
    val ratingValues = attributeDetails.find(_.name == "rating1").get.toValueOps.getValues(0, 10) ++ attributeDetails.find(_.name == "rating2").get.toValueOps.getValues(0, 10)
    ratingValues.groupBy(_.value).mapValues(_.map(_.frequency)).values.foreach(_ should contain only(2116, 4065))
    attributeDetails.foreach(attributeDetail => attributeOps.deleteAttribute(attributeDetail.id))
    attributeOps.getAllAttributes shouldBe empty
  }

  "NumericIntervalsAttributeBuilder" should "create attributes" in {
    val (ageField, salaryField) = {
      val fields = fieldOps.getAllFields
      (fields.find(_.name == "age").get, fields.find(_.name == "salary").get)
    }
    intercept[ValidationException] {
      dataset.toAttributeBuilder(dataset, NumericIntervalsAttribute("test", 0, List(NumericIntervalsAttribute.Bin("test", List(NumericIntervalsAttribute.Interval(InclusiveIntervalBorder(0), ExclusiveIntervalBorder(25))))), Nil)).build
    }
    intercept[ValidationException] {
      dataset.toAttributeBuilder(dataset, NumericIntervalsAttribute("test", ageField.id, List(), Nil)).build
    }
    intercept[ValidationException] {
      dataset.toAttributeBuilder(dataset, NumericIntervalsAttribute("test", ageField.id, List(NumericIntervalsAttribute.Bin("test", List())), Nil)).build
    }
    intercept[ValidationException] {
      dataset.toAttributeBuilder(dataset, NumericIntervalsAttribute("test", ageField.id, List(NumericIntervalsAttribute.Bin("test", List(NumericIntervalsAttribute.Interval(InclusiveIntervalBorder(20), ExclusiveIntervalBorder(0))))), Nil)).build
    }
    val attributeDetails = dataset.toAttributeBuilder(
      dataset,
      NumericIntervalsAttribute("age", ageField.id, List(
        NumericIntervalsAttribute.Bin("young", List(
          NumericIntervalsAttribute.Interval(InclusiveIntervalBorder(0), ExclusiveIntervalBorder(25)),
          NumericIntervalsAttribute.Interval(ExclusiveIntervalBorder(25), InclusiveIntervalBorder(30))
        )),
        NumericIntervalsAttribute.Bin("old", List(
          NumericIntervalsAttribute.Interval(InclusiveIntervalBorder(33), ExclusiveIntervalBorder(100))
        ))
      ), List(PreserveUncovered)),
      NumericIntervalsAttribute("salary", salaryField.id, List(
        NumericIntervalsAttribute.Bin("a", List(
          NumericIntervalsAttribute.Interval(InclusiveIntervalBorder(0), ExclusiveIntervalBorder(9000)),
          NumericIntervalsAttribute.Interval(ExclusiveIntervalBorder(10000), InclusiveIntervalBorder(20000))
        )),
        NumericIntervalsAttribute.Bin("b", List(
          NumericIntervalsAttribute.Interval(InclusiveIntervalBorder(9000), InclusiveIntervalBorder(10000))
        ))
      ), Nil)
    ).build
    attributeDetails.map(_.name) should contain only("age", "salary")
    attributeDetails.map(_.uniqueValuesSize) should contain only(2, 5)
    attributeDetails.find(_.name == "age").get.toValueOps.getValues(0, 10).map(_.value) should contain theSameElementsInOrderAs List("young", "25", "31", "32", "old")
    attributeDetails.find(_.name == "salary").get.toValueOps.getValues(0, 10).map(_.value) should contain theSameElementsInOrderAs List("a", "b")
    attributeDetails.find(_.name == "age").get.toValueOps.getValues(0, 10).find(_.value == "old").get.frequency shouldBe 4449
    attributeDetails.foreach(attributeDetail => attributeOps.deleteAttribute(attributeDetail.id))
    attributeOps.getAllAttributes shouldBe empty
  }

  "EquidistantIntervalsAttributeBuilder" should "create attributes" in {
    val (ageField, salaryField) = {
      val fields = fieldOps.getAllFields
      (fields.find(_.name == "age").get, fields.find(_.name == "salary").get)
    }
    intercept[ValidationException] {
      dataset.toAttributeBuilder(dataset, EquidistantIntervalsAttribute("test", ageField.id, 0, Nil)).build
    }
    val attributeDetails = dataset.toAttributeBuilder(
      dataset,
      EquidistantIntervalsAttribute("age1", ageField.id, 2, Nil),
      EquidistantIntervalsAttribute("salary", salaryField.id, 4, Nil),
      EquidistantIntervalsAttribute("age2", ageField.id, 5, Nil),
      EquidistantIntervalsAttribute("age3", ageField.id, 9, Nil),
      EquidistantIntervalsAttribute("salary2", salaryField.id, 9, Nil)
    ).build
    attributeDetails.map(_.name) should contain only("salary2", "age3", "age2", "salary", "age1")
    attributeDetails.map(_.uniqueValuesSize) should contain only(2, 4, 5, 9)
    attributeDetails.find(_.name == "age1").get.toValueOps.getValues(0, 10).map(_.value) should contain inOrderOnly("[20.0,42.5)", "[42.5,65.0]")
    attributeDetails.find(_.name == "age2").get.toValueOps.getValues(0, 10).map(_.value) should contain inOrderOnly("[20.0,29.0)", "[29.0,38.0)", "[38.0,47.0)", "[47.0,56.0)", "[56.0,65.0]")
    attributeDetails.find(_.name == "salary").get.toValueOps.getValues(0, 10).map(_.value) should contain inOrderOnly("[8110.0,9217.75)", "[9217.75,10325.5)", "[10325.5,11433.25)", "[11433.25,12541.0]")
    attributeDetails.find(_.name == "age1").get.toValueOps.getValues(0, 10).map(_.frequency) should contain inOrderOnly(3187, 2993)
    attributeDetails.find(_.name == "age2").get.toValueOps.getValues(0, 10).map(_.frequency) should contain inOrderOnly(1071, 1434, 1231, 1300, 1144)
    attributeDetails.find(_.name == "salary").get.toValueOps.getValues(0, 10).map(_.frequency) should contain inOrderOnly(3651, 1479, 340, 710)
    attributeDetails.foreach(attributeDetail => attributeOps.deleteAttribute(attributeDetail.id))
    attributeOps.getAllAttributes shouldBe empty
  }

  "EquifrequentIntervalsAttributeBuilder" should "create attributes" in {
    val (ageField, salaryField) = {
      val fields = fieldOps.getAllFields
      (fields.find(_.name == "age").get, fields.find(_.name == "salary").get)
    }
    intercept[ValidationException] {
      dataset.toAttributeBuilder(dataset, EquifrequentIntervalsAttribute("test", ageField.id, 0, Nil)).build
    }
    val attributes = (0 until 5).flatMap(i => List(EquifrequentIntervalsAttribute("age" + i, ageField.id, i * 4 + 2, Nil), EquifrequentIntervalsAttribute("salary" + i, salaryField.id, i * 4 + 2, Nil)))
    val attributeDetails = dataset.toAttributeBuilder(dataset, attributes: _*).build
    attributeDetails.length shouldBe attributes.length
    attributeDetails.map(_.name) should contain only (attributes.map(_.name).toSet.toList: _*)
    for (attributeDetail <- attributeDetails) {
      val attribute = attributes.find(_.name == attributeDetail.name).get
      val values = attributeDetail.toValueOps.getValues(0, 100)
      println(attribute.name + ": " + values.map(x => (x.value, x.frequency)))
      values.length shouldBe attribute.bins +- 1
      if (attributeDetail.name.startsWith("age")) {
        values.foreach(_.frequency shouldBe (math.ceil(dataset.size.toDouble / attribute.bins).toInt +- 150))
      }
    }
    attributeDetails.foreach(attributeDetail => attributeOps.deleteAttribute(attributeDetail.id))
    attributeOps.getAllAttributes shouldBe empty
  }

  "EquisizedIntervalsAttributeBuilder" should "create attributes" in {
    val (ageField, salaryField) = {
      val fields = fieldOps.getAllFields
      (fields.find(_.name == "age").get, fields.find(_.name == "salary").get)
    }
    intercept[ValidationException] {
      dataset.toAttributeBuilder(dataset, EquisizedIntervalsAttribute("test", ageField.id, 0.0001, Nil)).build
    }
    intercept[ValidationException] {
      dataset.toAttributeBuilder(dataset, EquisizedIntervalsAttribute("test", ageField.id, 1.1, Nil)).build
    }
    val attributes = (0 until 5).flatMap(i => List(EquisizedIntervalsAttribute("age" + i, ageField.id, 1.0 / (i * 4 + 2), Nil), EquisizedIntervalsAttribute("salary" + i, salaryField.id, 1.0 / (i * 4 + 2), Nil)))
    val attributeDetails = dataset.toAttributeBuilder(dataset, attributes: _*).build
    attributeDetails.length shouldBe attributes.length
    attributeDetails.map(_.name) should contain only (attributes.map(_.name).toSet.toList: _*)
    for (attributeDetail <- attributeDetails) {
      val attribute = attributes.find(_.name == attributeDetail.name).get
      val values = attributeDetail.toValueOps.getValues(0, 100)
      println(attribute.name + ": " + values.map(x => (x.value, x.frequency)) + ", minSupp: " + attribute.support + ", abs: " + (dataset.size * attribute.support))
      values.length.toDouble should be <= (1.0 / attribute.support)
      values.length should be >= 1
      all(values.map(_.frequency.toDouble)) should be >= (dataset.size * attribute.support)
    }
    attributeDetails.foreach(attributeDetail => attributeOps.deleteAttribute(attributeDetail.id))
    attributeOps.getAllAttributes shouldBe empty
  }

  "AttributeBuilders" should "create intervals with borders" in {
    val ageField = fieldOps.getAllFields.find(_.name == "age").get
    val attributeDetails = dataset.toAttributeBuilder(dataset, EquidistantIntervalsAttribute("age-1", ageField.id, 4, Nil), EquidistantIntervalsAttribute("age-2", ageField.id, 4, List(IntervalsBorder(Some(InclusiveIntervalBorder(20)), Some(ExclusiveIntervalBorder(30)))))).build
    attributeDetails.length shouldBe 2
    for (attributeDetail <- attributeDetails) {
      val values = attributeDetail.toValueOps.getValues(0, 100)
      values.length shouldBe 4
      if (attributeDetail.name == "age-1") {
        values.map(_.value) should contain only("[20.0,31.25)", "[31.25,42.5)", "[42.5,53.75)", "[53.75,65.0]")
      } else {
        values.map(_.value) should contain only("[20.0,22.25)", "[22.25,24.5)", "[24.5,26.75)", "[26.75,29.0]")
      }
    }
    attributeDetails.foreach(attributeDetail => attributeOps.deleteAttribute(attributeDetail.id))
    attributeOps.getAllAttributes shouldBe empty
  }

  it should "save uncovered values if there is the flag" in {
    val ageField = fieldOps.getAllFields.find(_.name == "age").get
    val intervals = List(
      NumericIntervalsAttribute.Bin("kids", List(
        NumericIntervalsAttribute.Interval(InclusiveIntervalBorder(0), ExclusiveIntervalBorder(15))
      )),
      NumericIntervalsAttribute.Bin("young", List(
        NumericIntervalsAttribute.Interval(InclusiveIntervalBorder(15), ExclusiveIntervalBorder(25)),
        NumericIntervalsAttribute.Interval(ExclusiveIntervalBorder(25), InclusiveIntervalBorder(30))
      )),
      NumericIntervalsAttribute.Bin("old", List(
        NumericIntervalsAttribute.Interval(InclusiveIntervalBorder(33), ExclusiveIntervalBorder(100))
      ))
    )
    val attributeDetails = dataset.toAttributeBuilder(
      dataset,
      NumericIntervalsAttribute("age-1", ageField.id, intervals, List(PreserveUncovered)),
      NumericIntervalsAttribute("age-2", ageField.id, intervals, Nil)
    ).build
    attributeDetails.map(_.uniqueValuesSize) should contain only(3, 6)
    for (attributeDetail <- attributeDetails) {
      val values = attributeDetail.toValueOps.getValues(0, 100)
      if (attributeDetail.name == "age-1") {
        values.map(_.value) should contain only("kids", "young", "old", "25", "31", "32")
        values.map(_.frequency) should contain only(0, 1381, 90, 99, 161, 4449)
      } else {
        values.map(_.value) should contain only("kids", "young", "old")
        values.map(_.frequency) should contain only(0, 1381, 4449)
      }
    }
    attributeDetails.foreach(attributeDetail => attributeOps.deleteAttribute(attributeDetail.id))
    attributeOps.getAllAttributes shouldBe empty
  }

  override protected def beforeAll(): Unit = {
    DBSpec.rollbackData()
    MysqlSchemaOps().createSchema()
  }

  override protected def afterAll(): Unit = {
    //DBSpec.rollbackData()
    mysqlDBConnector.close()
  }

}
