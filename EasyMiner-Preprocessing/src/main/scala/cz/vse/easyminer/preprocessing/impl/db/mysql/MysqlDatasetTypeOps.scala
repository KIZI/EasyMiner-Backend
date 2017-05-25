/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.preprocessing.impl.db.mysql

import cz.vse.easyminer.core.TaskStatusProcessor
import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.preprocessing.DatasetType.DatasetTypeOps
import cz.vse.easyminer.preprocessing._
import cz.vse.easyminer.preprocessing.impl.TypeableCases._
import cz.vse.easyminer.preprocessing.impl.db.MetaAttributeBuilder

/**
  * Created by Vaclav Zeman on 15. 2. 2016.
  */
class MysqlDatasetTypeOps private[db](implicit mysqlDBConnector: MysqlDBConnector, taskStatusProcessor: TaskStatusProcessor) extends DatasetTypeOps[LimitedDatasetType.type] {

  def toDatasetBuilder(dataset: Dataset): DatasetBuilder = MysqlDatasetBuilder(dataset)

  def toDatasetOps: DatasetOps = MysqlDatasetOps()

  def toAttributeOps(datasetDetail: DatasetDetail): AttributeOps = MysqlAttributeOps(datasetDetail)

  def toValueOps(datasetDetail: DatasetDetail, attributeDetail: AttributeDetail): ValueOps = MysqlValueOps(datasetDetail, attributeDetail)

  def toValueMapperOps(dataset: DatasetDetail): ValueMapperOps = MysqlValueMapperOps(dataset)

  def toAttributeBuilder(datasetDetail: DatasetDetail, attributes: Attribute*): AttributeBuilder[Attribute] = new MetaAttributeBuilder(datasetDetail, attributes, MysqlAttributeOps(datasetDetail))({
    case `Seq[SimpleAttribute]`(attributes) => MysqlSimpleAttributeBuilder(attributes, datasetDetail)
    case `Seq[NominalEnumerationAttribute]`(attributes) => MysqlNominalEnumerationAttributeBuilder(attributes, datasetDetail)
    case `Seq[NumericIntervalsAttribute]`(attributes) => MysqlNumericIntervalsAttributeBuilder(attributes, datasetDetail)
    case `Seq[EquidistantIntervalsAttribute]`(attributes) => MysqlEquidistantIntervalsAttributeBuilder(attributes, datasetDetail)
    case `Seq[EquifrequentIntervalsAttribute]`(attributes) => MysqlEquifrequentIntervalsAttributeBuilder(attributes, datasetDetail)
    case `Seq[EquisizedIntervalsAttribute]`(attributes) => MysqlEquisizedIntervalsAttributeBuilder(attributes, datasetDetail)
    case _ => throw new IllegalArgumentException
  })

}