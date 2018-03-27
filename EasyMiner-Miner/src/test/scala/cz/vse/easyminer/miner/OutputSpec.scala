package cz.vse.easyminer.miner

import cz.vse.easyminer.miner.impl._
import cz.vse.easyminer.miner.impl.io.{PmmlResult, PmmlTaskBuilder, PmmlTaskParser}
import cz.vse.easyminer.miner.impl.r.AruleExtractor
import cz.vse.easyminer.preprocessing._
import cz.vse.easyminer.preprocessing.impl.db.DatasetTypeConversions._
import cz.vse.easyminer.preprocessing.impl.db.mysql.Tables
import org.scalatest.{FlatSpec, Matchers, _}

import scala.language.implicitConversions
import scala.xml.XML

class OutputSpec extends FlatSpec with Matchers with ConfOpt with TemplateOpt {

  import MysqlPreprocessingDbOps._

  val datasetDetail: DatasetDetail = datasetBarbora.datasetDetail
  val attributes = datasetDetail.`type`.toAttributeOps(datasetDetail).getAllAttributes

  implicit def atributeValueToFixedValue(couple: (String, String))(implicit attributeMap: Map[String, AttributeDetail]): FixedValue = {
    val attribute = attributeMap(couple._1)
    val value = datasetDetail.`type`.toValueOps(datasetDetail, attribute).getValues(0, 1000).collectFirst {
      case ValueDetail(id, _, value, _) if value == couple._2 => id
    }.get
    FixedValue(attribute, value)
  }

  "RArule" should "should return correct arules from the R string representation" in {
    val ratingAttribute = attributes.find(_.name == "rating").get
    val ratingValues: Seq[ValueDetail] = datasetDetail.`type`.toValueOps(datasetDetail, ratingAttribute).getValues(0, 10)
    val aruleExtractor = AruleExtractor.getOutputARuleMapper(Count(datasetDetail.size), attributes)
    val strARule = s"1 {${ratingValues.tail.map(x => x.attribute + "=" + x.id).mkString(",")}} => {${ratingValues.head.attribute}=${ratingValues.head.id}} 0.5 0.7 0.9"
    Inside.inside(aruleExtractor.lift(strARule)) {
      case Some(aRule) =>
        aRule.antecedent should contain only (ratingValues.tail.map(x => FixedValue(ratingAttribute, x.id)): _*)
        aRule.consequent should contain only FixedValue(ratingAttribute, ratingValues.head.id)
        aRule.interestMeasures.getAll.toList should contain allOf (Support(0.5), Confidence(0.7), Lift(0.9))
        aRule.contingencyTable shouldBe ContingencyTable(Support(0.5), Confidence(0.7), Lift(0.9), Count(datasetDetail.size))
    }
  }

  it should "have right visualisation" in {
    implicit val attributeMap = attributes.map(x => x.name -> x).toMap
    val pmmlResult = new PmmlResult(
      MinerResult(
        new PmmlTaskParser(inputPmmlBarbora(datasetDetail, attributes).get).parse,
        Set.empty,
        Seq(
          ARule(List("rating" -> "A", "district" -> "Beroun"), List("rating" -> "B"), InterestMeasures(), ContingencyTable(5, 10, 15, 20)),
          ARule(List("district" -> "Praha"), List("rating" -> "C"), InterestMeasures(), ContingencyTable(5, 10, 15, 20))
        )
      ),
      datasetDetail.`type`.toValueMapperOps(datasetDetail)
    ) with ARuleText with BoolExpressionShortText
    pmmlResult.toPMML should include("<Text>rating(A) &amp; district(Beroun) → rating(B)</Text>")
    pmmlResult.toPMML should include("<Text>district(Praha) → rating(C)</Text>")
  }

  "PmmlTaskBuilder" should "create pmml from a miner task" in {
    implicit val attributeMap = attributes.map(x => x.name -> x).toMap
    val antecedent = NOT(Value[FixedValue]("district" -> "Praha") OR Value[FixedValue]("district" -> "Beroun")) AND Value(AllValues(attributeMap("age")))
    val consequent = NOT(Value[FixedValue]("rating" -> "D")) AND NOT(Value[FixedValue]("rating" -> "C")) AND Value[FixedValue]("rating" -> "A") AND Value[FixedValue]("rating" -> "B")
    val mt = MinerTask(datasetDetail, Some(antecedent), InterestMeasures(Confidence(0.5), Support(0.1), Lift(1.3), MaxRuleLength(4), Limit(100), CBA), Some(consequent))
    class PmmlTaskBuilderImpl(val templateParameters: Map[String, Any] = Map.empty) extends PmmlTaskBuilder {
      def apply(templateParameters: Map[String, Any]): PmmlTaskBuilder = new PmmlTaskBuilderImpl(templateParameters)

      protected[this] def datasetToInstanceTable(dataset: DatasetDetail) = new Tables.InstanceTable(dataset.id)
    }
    val pmml = XML.loadString(new PmmlTaskBuilderImpl().withDatabaseName("test").withMinerTask(mt).toPmml)
    (pmml \\ "DBASetting").size shouldBe 11
    (pmml \\ "BBASetting").size shouldBe 7
    (pmml \\ "InterestMeasureThreshold").size shouldBe 5
    """ id="(\d+?)" """.trim.r.findAllMatchIn(pmml.toString()).map(_.group(1).toInt).sum shouldBe 280
  }

}
