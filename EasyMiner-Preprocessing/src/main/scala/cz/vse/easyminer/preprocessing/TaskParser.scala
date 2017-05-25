/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.preprocessing

/**
 * Created by Vaclav Zeman on 1. 2. 2016.
 */
trait TaskParser {

 // val datasetDetail: DatasetDetail

  val attributes: Seq[Attribute]

  //def toAttributeBuilders: Seq[AttributeBuilder]

}
