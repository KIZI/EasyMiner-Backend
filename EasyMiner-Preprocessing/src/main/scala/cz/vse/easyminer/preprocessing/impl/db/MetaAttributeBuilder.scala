/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.preprocessing.impl.db

import cz.vse.easyminer.core.Validator
import cz.vse.easyminer.core.util.BasicFunction._
import cz.vse.easyminer.core.util.CollectionValidators
import cz.vse.easyminer.preprocessing._

import scala.util.Try

/**
  * Created by Vaclav Zeman on 29. 11. 2016.
  */
class MetaAttributeBuilder(val dataset: DatasetDetail, val attributes: Seq[Attribute], attributeOps: AttributeOps)(attributesToBuilder: Seq[Attribute] => AttributeBuilder[Attribute]) extends AttributeBuilder[Attribute] {

  /**
    * It converts Seq[Attribute] to List[AttributeBuilder[Attribute]] where Seq[Attribute] is grouped by attribute.getClass.getSimpleName
    * Then each group is separated to other groups where each attribute field ID is distinct within a group.
    * Builders are created separately by class of Attribute and field IDs
    */
  val builders = attributes.groupBy(_.getClass.getSimpleName).flatMap(
    _._2.foldLeft(List.empty[List[Attribute]])((y, x) => if (y.headOption.exists(_.forall(_.field != x.field))) (x :: y.head) :: y.tail else List(x) :: y)
  ).map(attributesToBuilder).toList

  /**
    * If one builder fails then all created attributed by prev builders will be removed
    *
    * @return
    */
  def build: Seq[AttributeDetail] = {
    Validator(builders)(CollectionValidators.NonEmpty)
    rollbackIfFailure(builders.map(x => Try(x.build)))(attributeDetails =>
      attributeDetails.foreach(attributeDetail => attributeOps.deleteAttribute(attributeDetail.id))
    ).flatten
  }

}
