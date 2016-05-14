package cz.vse.easyminer.miner

import cz.vse.easyminer.miner.impl.MinerTaskValidatorImpl
import cz.vse.easyminer.miner.impl.spark.FpGrowthMiner
import cz.vse.easyminer.preprocessing.NormalizedValue
import org.scalatest.{Matchers, FlatSpec}

/**
  * Created by propan on 6. 3. 2016.
  */
class SparkSpec extends FlatSpec with Matchers {

  import HivePreprocessingDbOps._

  lazy val miner: Miner = new FpGrowthMiner() with MinerTaskValidatorImpl

  "Spark miner" should "mine" in {
    import datasetBarbora._
    val task = MinerTask(datasetDetail, Some(Value(AllValues(attributeMap("district")))), InterestMeasures(Support(0.01), Confidence(0.9), Limit(1), MinRuleLength(1), MaxRuleLength(3)), Some(Value(AllValues(attributeMap("rating")))))
    val result = miner.mine(task)(_ => {})
    result.rules should matchPattern {
      case Seq(ARule(Some(Value(FixedValue(antAttr, antValue))), Value(FixedValue(conAttr, conValue)), im, ContingencyTable(63, 0, 3564, 2554)))
        if antAttr == attributeMap("district") && antValue == NormalizedValue(valueId("district", "Liberec")) &&
          conAttr == attributeMap("rating") && conValue == NormalizedValue(valueId("rating", "C")) &&
          im.confidence == 1.0 && im.support > 0.01 && im.support < 0.012 && im.lift > 1.7 =>
    }
  }

  it should "mine with auto params" in {
    import datasetBarbora._
    val task = MinerTask(datasetDetail, None, InterestMeasures(Limit(100), Auto), Some(Value(AllValues(attributeMap("rating")))))
    val result = miner.mine(task)(_ => {})
    result.rules.size should be > 0
  }

}
