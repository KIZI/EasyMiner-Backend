package cz.vse.easyminer.preprocessing.impl.parser

import cz.vse.easyminer.core.util.AnyToInt
import cz.vse.easyminer.preprocessing._

/**
 * Created by propan on 2. 2. 2016.
 */
class PmmlTaskParser(pmml: xml.NodeSeq) extends TaskParser {

  case class DerivedField(name: String, node: xml.Node)

  private val simpleAttributeBuilderExtractor: PartialFunction[DerivedField, Attribute] = new PartialFunction[DerivedField, Attribute] {
    def isDefinedAt(x: DerivedField): Boolean = (x.node \ "MapValues" \ "FieldColumnPair").headOption.exists(_.child.isEmpty)

    def apply(v1: DerivedField): Attribute = {
      val fieldId = AnyToInt.unapply(v1.node \ "MapValues" \ "FieldColumnPair" \@ "field").getOrElse(0)
      SimpleAttribute(v1.name, fieldId)
    }
  }

  lazy val attributes: Seq[Attribute] = (pmml \ "DerivedField").map { derivedFieldNode =>
    DerivedField(derivedFieldNode \@ "name", derivedFieldNode)
  }.collect(simpleAttributeBuilderExtractor)

}