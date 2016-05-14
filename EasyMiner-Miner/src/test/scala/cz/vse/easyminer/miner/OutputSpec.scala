package cz.vse.easyminer.miner

import cz.vse.easyminer.miner.impl._
import cz.vse.easyminer.miner.impl.r.AruleExtractor
import cz.vse.easyminer.preprocessing._
import cz.vse.easyminer.preprocessing.impl.db.DatasetTypeConversions._
import cz.vse.easyminer.preprocessing.impl.db.mysql.Tables
import cz.vse.easyminer.preprocessing.impl.db.mysql.Tables.InstanceTable
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
      case NominalValueDetail(id, _, value, _) if value == couple._2 => NormalizedValue(id)
    }.get
    FixedValue(attribute, value)
  }

  "RArule" should "should return correct arules from the R string representation" in {
    val ratingAttribute = attributes.find(_.name == "rating").get
    val ratingValueIds = datasetDetail.`type`.toValueOps(datasetDetail, ratingAttribute).getValues(0, 10).map(_.id)
    val instanceTable = new InstanceTable(datasetDetail.id, List(ratingAttribute.id))
    val aruleExtractor = new AruleExtractor(attributes).RArule
    Inside.inside(aruleExtractor.unapply(ratingValueIds.map(x => s"${instanceTable.columnById(ratingAttribute.id).value}=$x").mkString(",")).get._1) {
      case Some(Value(FixedValue(_, NormalizedValue(x1))) AND Value(FixedValue(_, NormalizedValue(x2))) AND Value(FixedValue(_, NormalizedValue(x3))) AND Value(FixedValue(_, NormalizedValue(x4)))) =>
        List(x1, x2, x3, x4) should contain only (ratingValueIds: _*)
    }
  }

  it should "have right visualisation" in {
    implicit val attributeMap = attributes.map(x => x.name -> x).toMap
    val pmmlResult = new PmmlResult(
      MinerResult(
        new PmmlTaskParser(inputPmmlBarbora(datasetDetail, attributes).get).parse,
        Set.empty,
        Seq(
          ARule(Some(Value[FixedValue]("rating" -> "A") AND Value("district" -> "Beroun")), Value("rating" -> "B"), InterestMeasures(), ContingencyTable(5, 10, 15, 20)),
          ARule(Some(Value("district" -> "Praha")), Value("rating" -> "C"), InterestMeasures(), ContingencyTable(5, 10, 15, 20))
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

      protected[this] def datasetToInstanceTable(dataset: DatasetDetail, attributeIds: Seq[Int]) = new Tables.InstanceTable(dataset.id, attributeIds)
    }
    val pmml = XML.loadString(new PmmlTaskBuilderImpl().withDatabaseName("test").withMinerTask(mt).toPmml)
    (pmml \\ "DBASetting").size shouldBe 11
    (pmml \\ "BBASetting").size shouldBe 7
    (pmml \\ "InterestMeasureThreshold").size shouldBe 5
    """ id="(\d+?)" """.trim.r.findAllMatchIn(pmml.toString()).map(_.group(1).toInt).sum shouldBe 280
  }

}
