/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.preprocessing

/**
 * Created by Vaclav Zeman on 27. 1. 2016.
 */
case class Instance(id: Int, values: Seq[Item])

case class NarrowInstance(id: Int, attribute: Int, value: Int)

case class Item(attribute: Int, value: Int)