package cz.vse.easyminer.preprocessing.impl.db

import cz.vse.easyminer.data.{SchemaOps => DataSchemaOps}
import cz.vse.easyminer.preprocessing.{SchemaOps => PreprocessingSchemaOps}

/**
 * Created by propan on 29. 12. 2015.
 */
trait DbSchemaOps extends PreprocessingSchemaOps {

  private[db] val dataSchemaOps: DataSchemaOps

  private[db] def preprocessingSchemaExists: Boolean

  private[db] def createPreprocessingSchema(): Unit

  final def schemaExists: Boolean = dataSchemaOps.schemaExists && preprocessingSchemaExists

  final def createSchema(): Unit = {
    if (!dataSchemaOps.schemaExists) dataSchemaOps.createSchema()
    if (!preprocessingSchemaExists) createPreprocessingSchema()
  }

}
