package cz.vse.easyminer.miner

import cz.vse.easyminer.core.util.Template
import cz.vse.easyminer.preprocessing.{AttributeDetail, DatasetDetail}
import org.scalatest._

import scala.xml._

class TemplateSpec extends FlatSpec with TemplateOpt with Matchers {

  "Template" should "have defaultBasePath /cz/vse/easyminer/" in {
    Template.defaultBasePath should be("/cz/vse/easyminer/")
  }

  it should "have R script string by name" in {
    rscript("", "", "", None, None, InterestMeasures())(rAprioriInitTemplate) should not be empty
    rscript("", "", "", None, None, InterestMeasures())(rAprioriProcessTemplate) should not be empty
  }

}

trait TemplateOpt extends ConfOpt {

  implicit val basePath = "/"

  def inputPmmlBarbora(datasetDetail: DatasetDetail, attributes: Seq[AttributeDetail]) = XML.loadString(
    Template(
      "InputPmmlBarbora.mustache",
      Map(
        "dataset-id" -> datasetDetail.id,
        "district-attribute-id" -> attributes.find(_.name == "district").get.id,
        "rating-attribute-id" -> attributes.find(_.name == "rating").get.id
      )
    )
  ).find(_.label == "PMML")

  def inputPmmlAudiology(datasetDetail: DatasetDetail, attributes: Seq[AttributeDetail], im: InterestMeasures) = XML.loadString(
    Template(
      "InputPmmlAudiology.mustache",
      Map(
        "dataset-id" -> datasetDetail.id,
        "im-limit" -> im.limit,
        "age-attribute-id" -> attributes.find(_.name == "age_gt_60").get.id,
        "air-attribute-id" -> attributes.find(_.name == "air").get.id,
        "airbonegap-attribute-id" -> attributes.find(_.name == "airBoneGap").get.id,
        "class-attribute-id" -> attributes.find(_.name == "class").get.id,
        "im-confidence" -> im.confidence,
        "im-support" -> im.support,
        "im-rulelength" -> im.maxlen
      )
    )
  ).find(_.label == "PMML")

  def rAprioriInitTemplate = "RAprioriInit.mustache"

  def rAprioriInitAutoTemplate = "RAprioriInitAuto.mustache"

  def rAprioriInitArulesTemplate = "RAprioriInitArules.mustache"

  def rAprioriProcessTemplate = "RAprioriProcess.mustache"

  def rscript(tableName: String, whereQuery: String, defaultAppearance: String, consequent: Option[String], both: Option[String], interestMeasures: InterestMeasures) = {
    val attributes = interestMeasures.getAll.foldLeft(Map(
      "jdbcDriverAbsolutePath" -> jdbcdriver,
      "dbServer" -> dbserver,
      "dbName" -> dbname,
      "dbUser" -> dbuser,
      "dbPassword" -> dbpassword,
      "dbTableName" -> tableName,
      "whereQuery" -> whereQuery,
      "defaultAppearance" -> defaultAppearance
    ): Map[String, Any]) {
      case (m, Confidence(x)) => m + ("confidence" -> x)
      case (m, Support(x)) => m + ("support" -> x)
      case (m, Lift(x)) => m + ("lift" -> x)
      case (m, Limit(x)) => m + ("limit" -> x)
      case (m, MinRuleLength(x)) => m + ("minlen" -> x)
      case (m, MaxRuleLength(x)) => m + ("maxlen" -> x)
      case (m, CBA) => m + ("cba" -> true)
      case (m, _) => m
    } ++ consequent.map(x => Map("consequent" -> x)).getOrElse(Map.empty) ++ both.map(x => Map("both" -> x)).getOrElse(Map.empty)
    (template: String) => Template(template, attributes)(Template.defaultBasePath)
  }

}
