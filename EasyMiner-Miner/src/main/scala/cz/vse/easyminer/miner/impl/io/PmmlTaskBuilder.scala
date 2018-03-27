package cz.vse.easyminer.miner.impl.io

import cz.vse.easyminer.core.util.Template
import cz.vse.easyminer.miner._
import cz.vse.easyminer.preprocessing.DatasetDetail
import cz.vse.easyminer.preprocessing.impl.db.mysql.Tables.InstanceTable

import scala.language.reflectiveCalls

/**
 * Created by propan on 4. 3. 2016.
 */
trait PmmlTaskBuilder {

  val templateParameters: Map[String, Any]

  def apply(templateParameters: Map[String, Any]): PmmlTaskBuilder

  protected[this] def datasetToInstanceTable(dataset: DatasetDetail): InstanceTable

  private sealed trait MergedExpression

  private case class Conjunction(exps: Set[MergedExpression]) extends MergedExpression

  private case class Disjunction(exps: Set[MergedExpression]) extends MergedExpression

  private case class Negation(exp: MergedExpression) extends MergedExpression

  private case class Literal(attribute: Attribute, positive: Boolean) extends MergedExpression

  private def boolExpressionToMergedExpression(exp: BoolExpression[Attribute]): MergedExpression = exp match {
    case exp1 AND exp2 => Conjunction(
      Set(boolExpressionToMergedExpression(exp1), boolExpressionToMergedExpression(exp2)).flatMap {
        case Conjunction(exps) => exps
        case x => Set(x)
      }
    )
    case exp1 OR exp2 => Disjunction(
      Set(boolExpressionToMergedExpression(exp1), boolExpressionToMergedExpression(exp2)).flatMap {
        case Disjunction(exps) => exps
        case x => Set(x)
      }
    )
    case NOT(Value(x)) => Literal(x, false)
    case NOT(x) => Negation(boolExpressionToMergedExpression(x))
    case Value(x) => Literal(x, true)
  }

  private def mergedExpressionToSet(mergedExpression: MergedExpression): Set[MergedExpression] = mergedExpression match {
    case mergedExpressionParent @ Conjunction(mergedExpressionChildren) => mergedExpressionChildren.map(mergedExpressionToSet).reduce(_ ++ _) + mergedExpressionParent
    case mergedExpressionParent @ Disjunction(mergedExpressionChildren) => mergedExpressionChildren.map(mergedExpressionToSet).reduce(_ ++ _) + mergedExpressionParent
    case mergedExpressionParent @ Negation(mergedExpressionChild) => mergedExpressionToSet(mergedExpressionChild) + mergedExpressionParent
    case mergedExpression: Literal => Set(mergedExpression)
  }

  private def mergedExpressionToDbaSetting(dbaSettingsIds: Map[MergedExpression, Int], bbaSettingsIds: Map[Attribute, Int])(mergedExpression: MergedExpression) = {
    val properties = mergedExpression match {
      case Conjunction(exps) => List("type" -> "Conjunction", "ba-refs" -> exps.map(dbaSettingsIds.apply))
      case Disjunction(exps) => List("type" -> "Disjunction", "ba-refs" -> exps.map(dbaSettingsIds.apply))
      case Negation(exp) => List("type" -> "Literal", "ba-refs" -> dbaSettingsIds(exp), "literal-sign" -> "Negative")
      case Literal(attribute, positive) => List("type" -> "Literal", "ba-refs" -> bbaSettingsIds(attribute), "literal-sign" -> (if (positive) "Positive" else "Negative"))
    }
    Map(("id" -> dbaSettingsIds(mergedExpression)) :: properties: _*)
  }

  private def attributeToBbaSetting(bbaSettingsIds: Map[Attribute, Int]): PartialFunction[Attribute, Map[String, Any]] = {
    case attribute @ AllValues(attributeDetail) => Map("id" -> bbaSettingsIds(attribute), "colname" -> attributeDetail.id, "allvalues" -> true)
    case attribute @ FixedValue(attributeDetail, normalizedValue) => Map("id" -> bbaSettingsIds(attribute), "colname" -> attributeDetail.id, "fixedvalue" -> normalizedValue)
  }

  private def interestMeasureToInterestMeasureThreshold(interestMeasuresIds: Map[InterestMeasure, Int]): PartialFunction[InterestMeasure, Map[String, Any]] = {
    case im @ Confidence(value) => Map("id" -> interestMeasuresIds(im), "name" -> "FUI", "value" -> value)
    case im @ Support(value) => Map("id" -> interestMeasuresIds(im), "name" -> "SUPP", "value" -> value)
    case im @ Lift(value) => Map("id" -> interestMeasuresIds(im), "name" -> "LIFT", "value" -> value)
    case im @ MaxRuleLength(value) => Map("id" -> interestMeasuresIds(im), "name" -> "RULE_LENGTH", "value" -> value)
    case im @ CBA => Map("id" -> interestMeasuresIds(im), "name" -> "CBA")
    case im @ Auto => Map("id" -> interestMeasuresIds(im), "name" -> "AUTO_CONF_SUPP")
  }

  def withMinerTask(minerTask: MinerTask) = {
    val antecedent = minerTask.antecedent.map(boolExpressionToMergedExpression)
    val consequent = minerTask.consequent.map(boolExpressionToMergedExpression)
    val dbaSettingsIds = List(antecedent, consequent).flatten.flatMap(mergedExpressionToSet).iterator.zipWithIndex.map(x => x._1 -> (x._2 + 1)).toMap
    val bbaSettingsIds = dbaSettingsIds.keySet.collect {
      case Literal(attribute, _) => attribute
    }.iterator.zipWithIndex.map(x => x._1 -> (x._2 + dbaSettingsIds.size + 1)).toMap
    val interestMeasuresIds = minerTask.interestMeasures.getAll.iterator.zipWithIndex.map(x => x._1 -> (x._2 + dbaSettingsIds.size + bbaSettingsIds.size + 1)).toMap
    val instanceTable = datasetToInstanceTable(minerTask.datasetDetail)
    apply(
      templateParameters ++ Map(
        List(
          "table-name" -> instanceTable.tableName,
          "im-limit" -> minerTask.interestMeasures.limit,
          "dba-settings" -> dbaSettingsIds.keys.map(mergedExpressionToDbaSetting(dbaSettingsIds, bbaSettingsIds)),
          "bba-settings" -> bbaSettingsIds.keys.collect(attributeToBbaSetting(bbaSettingsIds)),
          "ims" -> interestMeasuresIds.keys.collect(interestMeasureToInterestMeasureThreshold(interestMeasuresIds))
        ) ::: List(antecedent.map(x => "antecedent-id" -> dbaSettingsIds(x)), consequent.map(x => "consequent-id" -> dbaSettingsIds(x))).flatten: _*
      )
    )
  }

  def withDatabaseName(databaseName: String) = apply(
    templateParameters + ("database-name" -> databaseName)
  )

  def toPmml = Template("PmmlInputUnlimitedTask.mustache", templateParameters)

}