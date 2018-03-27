package cz.vse.easyminer.miner

import cz.vse.easyminer.core.Validator.ValidationException
import cz.vse.easyminer.miner.MinerResultHeader.MiningTime
import cz.vse.easyminer.miner.impl._
import cz.vse.easyminer.miner.impl.io.PmmlResult
import cz.vse.easyminer.miner.impl.r.AprioriMiner
import org.rosuda.REngine.Rserve.RConnection
import org.scalatest._

class MineSpec extends FlatSpec with Matchers with ConfOpt with TemplateOpt with PrivateMethodInvoker {

  import MysqlPreprocessingDbOps._
  import cz.vse.easyminer.preprocessing.impl.db.DatasetTypeConversions._

  implicit val rcp = RConnectionPoolImpl.defaultMiner

  def miner(r: RScript): Miner = new AprioriMiner(r) with MinerTaskValidatorImpl

  "R connection pooling" should "have minIdle = 2 and maxIdle = 10" in {
    val conn = new RConnectionPoolImpl(rserveAddress, rservePort, false) with RConnectionInit {
      def init(rConnection: RConnection): Unit = {}
    }
    def postBorrow(): Unit = conn.invokePrivate[RConnectionPoolImpl]("postBorrow")()
    def makeBorrowedConnections(num: Int) = (0 until num).map(_ => conn.borrow).toList
    var borrowedConnections = List.empty[BorrowedConnection]
    borrowedConnections = conn.borrow :: borrowedConnections
    postBorrow()
    conn.numIdle shouldBe 2
    borrowedConnections = borrowedConnections ::: makeBorrowedConnections(1)
    postBorrow()
    conn.refresh()
    conn.numIdle shouldBe 2
    borrowedConnections = borrowedConnections ::: makeBorrowedConnections(3)
    postBorrow()
    conn.numIdle shouldBe 2
    conn.numActive shouldBe 5
    borrowedConnections foreach conn.release
    Thread sleep 5000
    conn.numIdle shouldBe 7
    conn.numActive shouldBe 0
    borrowedConnections = makeBorrowedConnections(3)
    conn.numIdle shouldBe 4
    conn.numActive shouldBe 3
    borrowedConnections = borrowedConnections ::: makeBorrowedConnections(9)
    postBorrow()
    conn.numIdle shouldBe 2
    conn.numActive shouldBe 12
    borrowedConnections foreach conn.release
    Thread sleep 5000
    postBorrow()
    conn.numIdle shouldBe 10
    conn.numActive shouldBe 0
    conn.close()
  }

  "R Script with UTF8+space select query and consequents" should "return one association rule" in {
    import datasetBarbora._
    RScript evalTx { r =>
      val template = rscript(
        instanceTable.tableName,
        s"value=${valueId("district", "Jindřichův Hradec")} OR value=${valueId("district", "Jihlava")} OR value=${valueId("rating", "A")} OR value=${valueId("rating", "B")}",
        "lhs",
        Some( s""" "${colId("rating")}=${valueId("rating", "A")}","${colId("rating")}=${valueId("rating", "B")}" """.trim),
        None,
        InterestMeasures(Confidence(0.3), Support(0.001), MinRuleLength(1), MaxRuleLength(10), Limit(30000))
      )
      r.eval(template(rAprioriInitTemplate))
      r.eval(template(rAprioriInitArulesTemplate))
      r.eval(template(rAprioriProcessTemplate))
    } should have length 3
    RScript evalTx { r =>
      val template = rscript(
        instanceTable.tableName,
        s"value=${valueId("district", "Jihlava")} OR value=${valueId("rating", "C")}",
        "lhs",
        Some( s""" "${colId("rating")}=${valueId("rating", "C")}" """.trim),
        None,
        InterestMeasures(Confidence(0.9), Support(0.001), MinRuleLength(1), MaxRuleLength(10), Limit(30000))
      )
      r.eval(template(rAprioriInitTemplate))
      r.eval(template(rAprioriInitArulesTemplate))
      r.eval(template(rAprioriProcessTemplate))
    } should have length 3
  }

  "R Script with small support and confidence" should "return many results" in {
    import datasetBarbora._
    RScript.evalTx { r =>
      val template = rscript(
        instanceTable.tableName,
        "1",
        "lhs",
        Some( s""" "${colId("rating")}=${valueId("rating", "A")}","${colId("rating")}=${valueId("rating", "B")}","${colId("rating")}=${valueId("rating", "C")}","${colId("rating")}=${valueId("rating", "D")}" """.trim),
        None,
        InterestMeasures(Confidence(0.01), Support(0.001), MinRuleLength(1), MaxRuleLength(10), Limit(30000))
      )
      r.eval(template(rAprioriInitTemplate))
      r.eval(template(rAprioriInitArulesTemplate))
      r.eval(template(rAprioriProcessTemplate))
    }.length shouldBe 970
  }

  "AprioriMiner" should "be able to mine with a specific rule length" in {
    import datasetAudiology._
    RScript.evalTx { r =>
      val aprioriMiner = miner(r)
      val task = MinerTask(datasetDetail, None, InterestMeasures(Limit(100), Support(0.01), Confidence(0.9), MinRuleLength(1), MaxRuleLength(5)), None)
      for (i <- 1 to 3) {
        val result = aprioriMiner.mine(task.copy(interestMeasures = task.interestMeasures + MaxRuleLength(i) + MinRuleLength(i)))(_ => {})
        result.headers.foreach {
          case MinerResultHeader.InternalLimit(rulelen, size) =>
            rulelen shouldBe i
            rulelen match {
              case 2 => size shouldBe 7074
              case 3 => size shouldBe 361184
              case _ =>
            }
          case _ =>
        }
        result.rules.foreach(_.interestMeasures.maxlen shouldBe i)
        result.rules.size should be <= 100
      }
    }
  }

  it should "mine with timeout" in {
    import datasetAudiology._
    RScript.evalTx { r =>
      val aprioriMiner = miner(r)
      val task = MinerTask(datasetDetail, None, InterestMeasures(Limit(10000), Support(0.05), Confidence(0.9), MinRuleLength(1), MaxRuleLength(10)), Some(Value(AllValues(attributeMap("class")))))
      val result = aprioriMiner.mine(task)(_ => {})
      result.headers should contain oneOf(MinerResultHeader.Timeout(5), MinerResultHeader.Timeout(6))
      result.rules.size should be > 75
    }
  }

  it should "mine" in {
    import datasetBarbora._
    RScript.evalTx { r =>
      val aprioriMiner = miner(r)
      val task = MinerTask(datasetDetail, Some(Value(AllValues(attributeMap("district")))), InterestMeasures(Support(0.01), Confidence(0.9), Limit(1), MinRuleLength(1), MaxRuleLength(3)), Some(Value(AllValues(attributeMap("rating")))))
      val result = aprioriMiner.mine(task)(_ => {})
      result.rules should matchPattern {
        case Seq(ARule(List(FixedValue(antAttr, antValue)), List(FixedValue(conAttr, conValue)), im, ContingencyTable(63, 0, 3564, 2554)))
          if antAttr == attributeMap("district") && antValue == valueId("district", "Liberec") &&
            conAttr == attributeMap("rating") && conValue == valueId("rating", "C") &&
            im.confidence == 1.0 && im.support > 0.01 && im.support < 0.012 && im.lift > 1.7 =>
      }
      result.headers.collectFirst {
        case x: MiningTime => x
      } should matchPattern { case Some(MiningTime(p, m, f)) if p.toMillis > 0 && m.toMillis > 0 && f.toMillis >= 0 => }
    }
  }

  it should "mine with lift" in {
    import datasetBarbora._
    RScript.evalTx { r =>
      val aprioriMiner = miner(r)
      val task = MinerTask(datasetDetail, Some(Value(AllValues(attributeMap("district")))), InterestMeasures(Lift(1.3), Confidence(0.1), Support(0.001), Limit(100), MinRuleLength(1), MaxRuleLength(8)), Some(Value(AllValues(attributeMap("rating")))))
      val result = aprioriMiner.mine(task)(_ => {})
      result.rules.size should be > 0
      for (rule <- result.rules) {
        rule.interestMeasures.lift should be >= 1.3
      }
    }
  }

  it should "mine with rule length" in {
    RScript.evalTx { r =>
      import datasetBarbora._
      val lengthsAndResults = Seq(
        1 -> 2,
        2 -> 297,
        3 -> 917
      )
      val aprioriMiner = miner(r)
      val task = MinerTask(datasetDetail, Some(Value(AllValues(attributeMap("district"))) AND Value(AllValues(attributeMap("age")))), InterestMeasures(Confidence(0.1), Support(0.001), Limit(2000), MinRuleLength(1), MaxRuleLength(3)), Some(Value(AllValues(attributeMap("rating")))))
      for ((maxlen, expectedResult) <- lengthsAndResults) {
        val result = aprioriMiner.mine(task.copy(interestMeasures = task.interestMeasures + MaxRuleLength(maxlen)))(_ => {})
        result.rules.length shouldBe expectedResult
      }
    }
    RScript.evalTx { r =>
      import datasetAudiology._
      val aprioriMiner = miner(r)
      val task = MinerTask(
        datasetDetail,
        Some(Value(AllValues(attributeMap("age_gt_60"))) AND Value(AllValues(attributeMap("air"))) AND Value(AllValues(attributeMap("airBoneGap"))) AND Value(AllValues(attributeMap("ar_c"))) AND Value(AllValues(attributeMap("ar_u")))),
        InterestMeasures(Confidence(0.1), Support(0.001), Limit(2000), MinRuleLength(1), MaxRuleLength(5)),
        Some(Value(AllValues(attributeMap("class"))))
      )
      var i = 0
      var stages = 0
      val result = aprioriMiner.mine(task) { result =>
        i += result.rules.size
        stages += 1
      }
      result.rules.size shouldBe i
      stages shouldBe 3
    }
  }

  it should "mine with CBA" in {
    import datasetAudiology._
    val antecedent = Some(Value(AllValues(attributeMap("age_gt_60"))) AND Value(AllValues(attributeMap("air"))) AND Value(AllValues(attributeMap("airBoneGap"))) AND Value(AllValues(attributeMap("ar_c"))) AND Value(AllValues(attributeMap("ar_u"))))
    val consequent = Some(Value(AllValues(attributeMap("class"))))
    val withoutCba = RScript.evalTx { r =>
      val task = MinerTask(datasetDetail, antecedent, InterestMeasures(Confidence(0.01), Support(0.001), Limit(3000), MinRuleLength(1), MaxRuleLength(8)), consequent)
      miner(r).mine(task)(_ => {})
    }
    val withCba = RScript.evalTx { r =>
      val task = MinerTask(datasetDetail, antecedent, InterestMeasures(Confidence(0.01), Support(0.001), Limit(3000), MinRuleLength(1), MaxRuleLength(8), CBA), consequent)
      miner(r).mine(task)(_ => {})
    }
    val withCbaLimit = RScript.evalTx { r =>
      val task = MinerTask(datasetDetail, antecedent, InterestMeasures(Confidence(0.01), Support(0.001), Limit(100), MinRuleLength(1), MaxRuleLength(8), CBA), consequent)
      miner(r).mine(task)(_ => {})
    }
    val withCbaZero = RScript.evalTx { r =>
      val task = MinerTask(datasetDetail, antecedent, InterestMeasures(Confidence(0.9), Support(0.9), Limit(100), MinRuleLength(1), MaxRuleLength(3), CBA), consequent)
      miner(r).mine(task)(_ => {})
    }
    withoutCba.rules.length shouldBe 1699
    withCba.rules.length shouldBe 38
    withCbaLimit.headers should contain(MinerResultHeader.InternalLimit(3, 638))
    withCbaLimit.rules.length shouldBe 20
    withCbaLimit.rules.foreach(_.interestMeasures.lift should be > 0.0)
    withCbaZero.rules.length shouldBe 0
  }

  it should "mine with limit 100 and return 100 with one empty antecedent" in {
    import datasetBarbora._
    val limitedResult = RScript.evalTx { r =>
      val task = MinerTask(datasetDetail, Some(Value(AllValues(attributeMap("district")))), InterestMeasures(Confidence(0.1), Support(0.001), Limit(100), MinRuleLength(1), MaxRuleLength(4)), Some(Value(AllValues(attributeMap("rating")))))
      miner(r).mine(task)(_ => {})
    }
    limitedResult.rules.length shouldBe 100
    val emptyAntecedent = limitedResult.rules.filter(_.antecedent.isEmpty)
    emptyAntecedent.length shouldBe 1
    val pmml = (new PmmlResult(limitedResult.copy(rules = emptyAntecedent), datasetDetail.`type`.toValueMapperOps(datasetDetail)) with ARuleText with BoolExpressionShortText).toPMML
    pmml should not include "<Text>()</Text>"
    pmml should not include "<FieldRef></FieldRef>"
    pmml should not include "antecedent="
    pmml should include( """ <FourFtTable a="3627" b="2554" c="0" d="0" """.trim)
    RScript.evalTx { r =>
      val task = MinerTask(datasetDetail, Some(Value(AllValues(attributeMap("district")))), InterestMeasures(Confidence(0.1), Support(0.01), Limit(100), MinRuleLength(1), MaxRuleLength(4)), Some(Value(AllValues(attributeMap("rating")))))
      miner(r).mine(task)(_ => {}).rules
    }.length shouldBe 22
  }

  it should "throw an exception due to bad interest measure values" in {
    import datasetBarbora._
    val badInterestMeasures: Seq[InterestMeasures] = Seq(
      InterestMeasures(),
      InterestMeasures(Support(0.5)),
      InterestMeasures(Confidence(0.5)),
      InterestMeasures(Support(0.5), Confidence(0.5)),
      InterestMeasures(Support(0.5), Confidence(0.5), Limit(100)),
      InterestMeasures(Support(0.5), Confidence(0.5), Limit(100), MinRuleLength(1)),
      InterestMeasures(Support(1.1), Confidence(0.5), Limit(100), MinRuleLength(1), MaxRuleLength(3)),
      InterestMeasures(Confidence(0.5), Support(1.1), Limit(100), MinRuleLength(1), MaxRuleLength(3)),
      InterestMeasures(Support(0.0009), Confidence(0.5), Limit(100), MinRuleLength(1), MaxRuleLength(3)),
      InterestMeasures(Support(0.5), Confidence(0.0009), Limit(100), MinRuleLength(1), MaxRuleLength(3)),
      InterestMeasures(Support(0.5), Confidence(0.5), Limit(0), MinRuleLength(1), MaxRuleLength(3)),
      InterestMeasures(Support(0.5), Confidence(0.5), Limit(100), MinRuleLength(3), MaxRuleLength(2))
    )
    for (im <- badInterestMeasures) intercept[ValidationException] {
      RScript.evalTx { r =>
        val task = MinerTask(datasetDetail, Some(Value(AllValues(attributeMap("district")))), im, Some(Value(AllValues(attributeMap("rating")))))
        miner(r).mine(task)(_ => {})
      }
    }
  }

  it should "throw an exception due to bad attributes for CBA" in {
    import datasetBarbora._
    val badAttributes: Seq[BoolExpression[Attribute]] = Seq(
      Value(AllValues(attributeMap("rating"))) AND Value(AllValues(attributeMap("age"))),
      Value(AllValues(attributeMap("rating"))) AND Value(FixedValue(attributeMap("age"), valueId("age", "51"))),
      Value(*)
    )
    for (attr <- badAttributes) intercept[ValidationException] {
      RScript.evalTx { r =>
        val task = MinerTask(datasetDetail, Some(Value(AllValues(attributeMap("district")))), InterestMeasures(Support(0.5), Confidence(0.5), Limit(100), MinRuleLength(1), MaxRuleLength(3), CBA), Some(attr))
        miner(r).mine(task)(_ => {})
      }
    }
  }

  it should "mine with an empty antecedent or consequent" in {
    import datasetBarbora._
    val emptyConsequentResult = RScript.evalTx { r =>
      val task = MinerTask(datasetDetail, Some(Value(FixedValue(attributeMap("age"), valueId("age", "25")))), InterestMeasures(Support(0.001), Confidence(0.01), Limit(200), MinRuleLength(1), MaxRuleLength(8)), None)
      miner(r).mine(task)(_ => {}).rules
    }
    emptyConsequentResult.size shouldBe 105
    for (arule <- emptyConsequentResult if arule.antecedent.nonEmpty) {
      arule.antecedent should matchPattern { case List(FixedValue(attr, value)) if attr == attributeMap("age") && value == valueId("age", "25") => }
    }
    val emptyAntecedentResult = RScript.evalTx { r =>
      val task = MinerTask(datasetDetail, None, InterestMeasures(Support(0.001), Confidence(0.5), Limit(200), MinRuleLength(1), MaxRuleLength(8)), Some(Value(AllValues(attributeMap("rating")))))
      miner(r).mine(task)(_ => {}).rules
    }
    emptyAntecedentResult.size shouldBe 200
    for (arule <- emptyAntecedentResult) {
      arule.consequent should matchPattern { case List(FixedValue(attr, _)) if attr == attributeMap("rating") => }
    }
    val emptyBothResult = RScript.evalTx { r =>
      val task = MinerTask(datasetDetail, None, InterestMeasures(Support(0.01), Confidence(0.5), Limit(200), MinRuleLength(1), MaxRuleLength(8)), None)
      miner(r).mine(task)(_ => {}).rules
    }
    emptyBothResult.size shouldBe 52
    emptyBothResult.foreach(_.interestMeasures.support should be >= 0.01)
  }

  it should "be able to mine with a same attribute in both sides" in {
    import datasetBarbora._
    val result = RScript.evalTx { r =>
      val task = MinerTask(datasetDetail, Some(Value(AllValues(attributeMap("rating"))) AND Value(FixedValue(attributeMap("age"), valueId("age", "31")))), InterestMeasures(Support(0.001), Confidence(0.04), Limit(200), MinRuleLength(1), MaxRuleLength(8)), Some(Value(AllValues(attributeMap("rating"))) AND Value(AllValues(attributeMap("age"))) AND Value(FixedValue(attributeMap("district"), valueId("district", "Praha")))))
      miner(r).mine(task)(_ => {}).rules
    }
    result.size shouldBe 35
  }

  it should "mine with auto param" in {
    import datasetBarbora._
    RScript.evalTx { r =>
      val task = MinerTask(datasetDetail, None, InterestMeasures(Limit(100), MaxRuleLength(2), Auto), Some(Value(AllValues(attributeMap("rating")))))
      val rules = miner(r).mine(task)(_ => {}).rules
      rules.size should be > 0
      rules.foreach(_.interestMeasures.maxlen <= 2)
    }
    RScript.evalTx { r =>
      val task = MinerTask(datasetDetail, None, InterestMeasures(Limit(100), Auto, CBA), Some(Value(AllValues(attributeMap("rating")))))
      miner(r).mine(task)(_ => {}).rules.size should be > 0
    }
    RScript.evalTx { r =>
      val task = MinerTask(datasetDetail, Some(Value(AllValues(attributeMap("district"))) AND Value(AllValues(attributeMap("age")))), InterestMeasures(Limit(100), Auto), Some(Value(FixedValue(attributeMap("rating"), valueId("rating", "A"))) OR Value(FixedValue(attributeMap("rating"), valueId("rating", "B")))))
      miner(r).mine(task)(_ => {}).rules.size should be > 0
    }
  }

}