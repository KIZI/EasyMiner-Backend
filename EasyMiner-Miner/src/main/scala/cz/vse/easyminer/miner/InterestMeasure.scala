package cz.vse.easyminer.miner

import scala.reflect.ClassTag

sealed trait InterestMeasure

class InterestMeasures private(imm: Map[String, InterestMeasure]) {

  def +(interestMeasure: InterestMeasure) = new InterestMeasures(imm + (interestMeasure.getClass.getSimpleName -> interestMeasure))

  def remove[T <: InterestMeasure : ClassTag](implicit ct: ClassTag[T]) = new InterestMeasures(imm - ct.runtimeClass.getSimpleName)

  private def getValue[T <: InterestMeasure](clazz: Class[T]) = imm(clazz.getSimpleName).asInstanceOf[T]

  def has(interestMeasure: InterestMeasure) = imm.contains(interestMeasure.getClass.getSimpleName)

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

case class Confidence(value: Double) extends InterestMeasure

case class Support(value: Double) extends InterestMeasure

case class Lift(value: Double) extends InterestMeasure

case class Count(value: Int) extends InterestMeasure

case class Limit(value: Int) extends InterestMeasure

case class MinRuleLength(value: Int) extends InterestMeasure

case class MaxRuleLength(value: Int) extends InterestMeasure

case object CBA extends InterestMeasure

case object Auto extends InterestMeasure