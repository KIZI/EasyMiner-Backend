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
import cz.vse.easyminer.preprocessing.impl.db.AttributeConversions._

/**
  * Created by Vaclav Zeman on 15. 2. 2016.
  */

/**
  * Class for dataset type operations.
  * Operations are for creation of dataset/attribute/value operations objects
  *
  * @param mysqlDBConnector    mysql database connection
  * @param taskStatusProcessor task processor for monitoring
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
    case `Seq[EquidistantIntervalsAttribute]`(attributes) => MysqlDiscretizationAttributeBuilder(attributes, datasetDetail)
    case `Seq[EquifrequentIntervalsAttribute]`(attributes) => MysqlDiscretizationAttributeBuilder(attributes, datasetDetail)
    case `Seq[EquisizedIntervalsAttribute]`(attributes) => MysqlDiscretizationAttributeBuilder(attributes, datasetDetail)
    case _ => throw new IllegalArgumentException
  })

}