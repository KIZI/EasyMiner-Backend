/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.core.util

import scala.language.implicitConversions

/**
  * Created by Vaclav Zeman on 9. 10. 2016.
  */
object XmlUtils {

  case class TrimmedXml(node: xml.Node)

  implicit def XmlToTrimmedXml(untrimmedXml: xml.NodeSeq): TrimmedXml = TrimmedXml(xml.Utility.trim(untrimmedXml.head))

}
