package cz.vse.easyminer.core.util

import scala.language.implicitConversions

/**
  * Created by propan on 9. 10. 2016.
  */
object XmlUtils {

  case class TrimmedXml(node: xml.Node)

  implicit def XmlToTrimmedXml(untrimmedXml: xml.NodeSeq): TrimmedXml = TrimmedXml(xml.Utility.trim(untrimmedXml.head))

}
