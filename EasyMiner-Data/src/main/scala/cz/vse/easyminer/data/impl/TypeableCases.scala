/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data.impl

import cz.vse.easyminer.core.ResultTaskStatus
import cz.vse.easyminer.data.ValueInterval
import shapeless.TypeCase

/**
 * Created by Vaclav Zeman on 10. 2. 2016.
 */
object TypeableCases {

  val `Seq[ValueInterval]` = TypeCase[Seq[ValueInterval]]
  val `ResultTaskStatus[Seq[ValueInterval]]` = TypeCase[ResultTaskStatus[Seq[ValueInterval]]]

}
