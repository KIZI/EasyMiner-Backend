/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.miner

import scala.reflect.ClassTag

/**
  * Interest measure for a rule
  */
sealed trait InterestMeasure

/**
  * Collection of interest measures for a rule
  *
  * @param imm map of interest measures
  */
class InterestMeasures private(imm: Map[String, InterestMeasure]) {

  /**
    * Add new interest measure for a rule
    *
    * @param interestMeasure new interest measure
    * @return new interest measures object with added measure
    */
  def +(interestMeasure: InterestMeasure) = new InterestMeasures(imm + (interestMeasure.getClass.getSimpleName -> interestMeasure))

  /**
    * Remove interest measure
    *
    * @param ct class of interest measure
    * @tparam T type of interest measure
    * @return new interest measures object with removed measure
    */
  def remove[T <: InterestMeasure : ClassTag](implicit ct: ClassTag[T]) = new InterestMeasures(imm - ct.runtimeClass.getSimpleName)

  /**
    * Get an interest measure
    *
    * @param clazz class of interest measure
    * @tparam T type if interest measure
    * @return interest measure
    */
  private def getValue[T <: InterestMeasure](clazz: Class[T]) = imm(clazz.getSimpleName).asInstanceOf[T]

  /**
    * Check whether there is an interest measure within collection
    *
    * @param interestMeasure interest measure
    * @return true if the interest measure is included
    */
  def has(interestMeasure: InterestMeasure) = imm.contains(interestMeasure.getClass.getSimpleName)

  /**
    * Check whether there is a type of an interest measure within collection
    *
    * @param ct class of interest measure
    * @tparam T type of interest measure
    * @return true if the type of interest measure is included
    */
  def hasType[T <: InterestMeasure : ClassTag](implicit ct: ClassTag[T]) = imm.contains(ct.runtimeClass.getSimpleName)

  def confidence = getValue(classOf[Confidence]).value

  def support = getValue(classOf[Support]).value

  def lift = getValue(classOf[Lift]).value

  def limit = getValue(classOf[Limit]).value

  def count = getValue(classOf[Count]).value

  def maxlen = getValue(classOf[MaxRuleLength]).value

  def minlen = getValue(classOf[MinRuleLength]).value

  def getAll = imm.values

}

object InterestMeasures {

  def apply(ims: InterestMeasure*): InterestMeasures = apply(ims)

  def apply(ims: Iterable[InterestMeasure]): InterestMeasures = new InterestMeasures(ims.map(im => im.getClass.getSimpleName -> im).toMap)

}

/**
  * Confidence of a rule
  *
  * @param value confidence number
  */
case class Confidence(value: Double) extends InterestMeasure

/**
  * Relative support of a rule
  *
  * @param value support number
  */
case class Support(value: Double) extends InterestMeasure

/**
  * Lift of a rule
  *
  * @param value lift number
  */
case class Lift(value: Double) extends InterestMeasure

/**
  * Total number of instances/transactions
  *
  * @param value number
  */
case class Count(value: Int) extends InterestMeasure

/**
  * Maximal number of rules
  *
  * @param value limit number
  */
case class Limit(value: Int) extends InterestMeasure

/**
  * Min rule length
  *
  * @param value min number
  */
case class MinRuleLength(value: Int) extends InterestMeasure

/**
  * Max rule length
  *
  * @param value max number
  */
case class MaxRuleLength(value: Int) extends InterestMeasure

/**
  * We want only pruned rules
  */
case object CBA extends InterestMeasure

/**
  * We want rules where support and confidence will be detected automatically
  */
case object Auto extends InterestMeasure