/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.miner

case class ContingencyTable(a: Int, b: Int, c: Int, d: Int) {
  def support = Support(a.toDouble / (a + b + c + d))

  def confidence = Confidence(a.toDouble / (a + b))

  def lift = confidence.value / ((a + c).toDouble / (a + b + c + d))

  def count = Count(a + b + c + d)
}

object ContingencyTable {

  def apply(supp: Support, conf: Confidence, lift: Lift, count: Count) = {
    val a = math.rint(supp.value * count.value).toInt
    val b = math.rint(a / conf.value - a).toInt
    val c = math.rint((conf.value * count.value) / lift.value - a).toInt
    val d = count.value - a - b - c
    new ContingencyTable(a, b, c, d)
  }

}