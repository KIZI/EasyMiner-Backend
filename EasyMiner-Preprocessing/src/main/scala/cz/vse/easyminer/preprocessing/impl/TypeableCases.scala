package cz.vse.easyminer.preprocessing.impl

import cz.vse.easyminer.data.ValueInterval
import cz.vse.easyminer.preprocessing.{AttributeDetail, SimpleAttribute}
import shapeless.TypeCase

/**
 * Created by propan on 10. 2. 2016.
 */
object TypeableCases {

  val `Seq[AttributeDetail]` = TypeCase[Seq[AttributeDetail]]
  val `Seq[ValueInterval]` = TypeCase[Seq[ValueInterval]]
  val `Seq[SimpleAttribute]` = TypeCase[Seq[SimpleAttribute]]

}
