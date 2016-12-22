package cz.vse.easyminer.preprocessing.impl

import cz.vse.easyminer.data.ValueInterval
import cz.vse.easyminer.preprocessing._
import shapeless.TypeCase

/**
 * Created by propan on 10. 2. 2016.
 */
object TypeableCases {

  val `Seq[AttributeDetail]` = TypeCase[Seq[AttributeDetail]]
  val `Seq[ValueInterval]` = TypeCase[Seq[ValueInterval]]
  val `Seq[SimpleAttribute]` = TypeCase[Seq[SimpleAttribute]]
  val `Seq[NominalEnumerationAttribute]` = TypeCase[Seq[NominalEnumerationAttribute]]
  val `Seq[NumericIntervalsAttribute]` = TypeCase[Seq[NumericIntervalsAttribute]]
  val `Seq[EquidistantIntervalsAttribute]` = TypeCase[Seq[EquidistantIntervalsAttribute]]
  val `Seq[EquifrequentIntervalsAttribute]` = TypeCase[Seq[EquifrequentIntervalsAttribute]]
  val `Seq[EquisizedIntervalsAttribute]` = TypeCase[Seq[EquisizedIntervalsAttribute]]

}
