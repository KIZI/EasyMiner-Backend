/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.miner

/**
  * 4ft contingency table object
  *
  * @param a number of instances: Antecedent & Consequent
  * @param b number of instances: Antecedent & !Consequent
  * @param c number of instances: !Antecedent & Consequent
  * @param d number of instances: !Antecedent & !Consequent
  */
case class ContingencyTable(a: Int, b: Int, c: Int, d: Int) {
  /**
    * Relative support of a rule
    *
    * @return support number
    */
  def support = Support(a.toDouble / (a + b + c + d))

  /**
    * Confidence of a rule
    *
    * @return confidence number
    */
  def confidence = Confidence(a.toDouble / (a + b))

  /**
    * Lift of a rule
    *
    * @return lift rule
    */
  def lift = confidence.value / ((a + c).toDouble / (a + b + c + d))

  /**
    * Number of all instances
    *
    * @return number
    */
  def count = Count(a + b + c + d)
}

object ContingencyTable {

  /**
    * This creates contingency table from support, confidence, lift and total number of instances
    *
    * @param supp  relative support
    * @param conf  confidence
    * @param lift  lift
    * @param count total number of instances
    * @return contingency table
    */
  def apply(supp: Support, conf: Confidence, lift: Lift, count: Count) = {
    val a = math.rint(supp.value * count.value).toInt
    val b = math.rint(a / conf.value - a).toInt
    val c = math.rint((conf.value * count.value) / lift.value - a).toInt
    val d = count.value - a - b - c
    new ContingencyTable(a, b, c, d)
  }

}