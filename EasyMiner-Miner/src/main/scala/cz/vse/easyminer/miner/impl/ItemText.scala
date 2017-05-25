/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.miner.impl

import cz.vse.easyminer.core.util.AnyToInt
import cz.vse.easyminer.preprocessing.Item

/**
  * Created by Vaclav Zeman on 3. 10. 2016.
  */
object ItemText {

  def apply(item: Item) = s"${item.attribute}=${item.value}"

  def unapply(arg: String): Option[Item] = {
    val ItemRegExp = "(\\d+)=(\\d+)".r
    arg match {
      case ItemRegExp(AnyToInt(attribute), AnyToInt(value)) => Some(Item(attribute, value))
      case _ => None
    }
  }

}
