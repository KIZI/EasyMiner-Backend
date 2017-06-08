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

/**
  * Utils for XML processing
  */
object XmlUtils {

  case class TrimmedXml(node: xml.Node)

  /**
    * Implicit function for convert XML node seq into trimmed xml, where there are no spaces among tags.
    *
    * @param untrimmedXml xml input
    * @return trimmed xml output
    */
  implicit def XmlToTrimmedXml(untrimmedXml: xml.NodeSeq): TrimmedXml = TrimmedXml(xml.Utility.trim(untrimmedXml.head))

}
