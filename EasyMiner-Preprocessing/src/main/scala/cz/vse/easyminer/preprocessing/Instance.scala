package cz.vse.easyminer.preprocessing

/**
 * Created by propan on 27. 1. 2016.
 */
case class Instance(id: Int, values: Seq[Item])

case class NarrowInstance(id: Int, attribute: Int, value: Int)

case class Item(attribute: Int, value: Int)