package cz.vse.easyminer.data.impl

import cz.vse.easyminer.core.ResultTaskStatus
import cz.vse.easyminer.data.ValueInterval
import shapeless.TypeCase

/**
 * Created by propan on 10. 2. 2016.
 */
object TypeableCases {

  val `Seq[ValueInterval]` = TypeCase[Seq[ValueInterval]]
  val `ResultTaskStatus[Seq[ValueInterval]]` = TypeCase[ResultTaskStatus[Seq[ValueInterval]]]

}
