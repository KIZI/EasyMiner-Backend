package cz.vse.easyminer.preprocessing.impl.db.mysql

import cz.vse.easyminer.core.TaskStatusProcessor
import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.preprocessing.{AttributeDetail, CollectiveAttributeBuilder, DatasetDetail, SimpleAttribute}

/**
  * Created by propan on 12. 5. 2016.
  */
class MysqlCollectiveSimpleAttributeBuilder private(val dataset: DatasetDetail, val attributes: Seq[SimpleAttribute])
                                                   (implicit
                                                    mysqlDBConnector: MysqlDBConnector,
                                                    taskStatusProcessor: TaskStatusProcessor) extends CollectiveAttributeBuilder[SimpleAttribute] {

  def build: Seq[AttributeDetail] = {
    attributes.map(attribute => MysqlSimpleAttributeBuilder(attribute, dataset).build)
  }

}

object MysqlCollectiveSimpleAttributeBuilder {

  def apply(attributes: Seq[SimpleAttribute], dataset: DatasetDetail)(implicit mysqlDBConnector: MysqlDBConnector, taskStatusProcessor: TaskStatusProcessor): CollectiveAttributeBuilder[SimpleAttribute] = {
    new MysqlCollectiveSimpleAttributeBuilder(dataset, attributes)
  }

}