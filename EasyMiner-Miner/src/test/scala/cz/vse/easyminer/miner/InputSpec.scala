package cz.vse.easyminer.miner

import cz.vse.easyminer.data.NominalValue
import cz.vse.easyminer.miner.impl.MinerTaskValidatorImpl.Exceptions.BadInterestMeasureInput
import cz.vse.easyminer.miner.impl.io.PmmlTaskParser
import cz.vse.easyminer.miner.impl.MinerTaskValidatorImpl
import cz.vse.easyminer.miner.impl.io.PmmlTaskParser.Exceptions.NoDataset
import org.scalatest.{FlatSpec, Inside, Matchers}

import scala.xml.{Elem, Node}

class InputSpec extends FlatSpec with Matchers with TemplateOpt {

  import MysqlPreprocessingDbOps._
  import cz.vse.easyminer.preprocessing.impl.db.DatasetTypeConversions._
  import datasetBarbora._

  lazy val task1 = inputPmmlBarbora(datasetDetail, datasetDetail.`type`.toAttributeOps(datasetDetail).getAllAttributes).get

  def removeXmlElements(xml: Node, elemNames: String*): Node = xml match {
    case elem: Elem =>
      val children = elem.child.filter(x => !elemNames.contains(x.label)).map(x => removeXmlElements(x, elemNames: _*))
      elem.copy(child = children)
    case _ => xml
  }

  def removeXmlElementsWithId(xml: Node, ids: String*): Node = xml match {
    case elem: Elem =>
      val children = elem.child.filter(x => !ids.contains(x \@ "id")).map(x => removeXmlElementsWithId(x, ids: _*))
      elem.copy(child = children)
    case _ => xml
  }

  "PMML Task" should "have antecedent and consequent" in {
    val minerTask = new PmmlTaskParser(task1).parse
    minerTask.antecedent should matchPattern {
      case Some(Value(AllValues(x))) if x.name == "district" =>
    }
    Inside.inside(minerTask.consequent) {
      case Some(Value(FixedValue(x, y))) if x.name == "rating" && x.uniqueValuesSize == 4 =>
        datasetDetail.`type`.toValueMapperOps(datasetDetail).itemMapper(Map(x -> Set(y))).value(x, y) should matchPattern {
          case Some(NominalValue("A")) =>
        }
    }
    val task3 = new PmmlTaskParser(removeXmlElements(task1, "AntecedentSetting", "ConsequentSetting")).parse
    task3.antecedent shouldBe None
    task3.consequent shouldBe None
  }

  it should "have all interest measures" in {
    val minerTask = new PmmlTaskParser(task1).parse
    val im = minerTask.interestMeasures
    im.hasType[Lift] shouldBe true
    im.hasType[Confidence] shouldBe true
    im.hasType[Support] shouldBe true
    im.hasType[Limit] shouldBe true
    im.hasType[MinRuleLength] shouldBe true
    im.hasType[MaxRuleLength] shouldBe true
    im.has(CBA) shouldBe true
  }

  it should "not have optional interest measures" in {
    val minerTask = new PmmlTaskParser(removeXmlElementsWithId(task1, "15", "60")).parse
    minerTask.interestMeasures.getAll.exists {
      case Lift(_) | CBA => true
      case _ => false
    } shouldBe false
    val im = minerTask.interestMeasures
    im.hasType[Confidence] shouldBe true
    im.hasType[Support] shouldBe true
    im.hasType[Limit] shouldBe true
    im.hasType[MinRuleLength] shouldBe true
    im.hasType[MaxRuleLength] shouldBe true
  }

  it should "not be valid" in {
    val minerTask1 = new PmmlTaskParser(removeXmlElementsWithId(task1, "5")).parse
    val minerTask2 = new PmmlTaskParser(task1).parse
    val validator = new MinerTaskValidatorImpl {}
    intercept[BadInterestMeasureInput] {
      validator.validate(minerTask1)
    }
    validator.validate(minerTask2)
    intercept[BadInterestMeasureInput] {
      val fixedValue = minerTask2.consequent.get.asInstanceOf[Value[FixedValue]].x
      validator.validate(minerTask2.copy(consequent = Some(minerTask2.consequent.get AND Value(FixedValue(fixedValue.attributeDetail.copy(id = 999), 999)))))
    }
    intercept[NoDataset.type] {
      new PmmlTaskParser(removeXmlElements(task1, "Extension")).parse
    }

  }

}
