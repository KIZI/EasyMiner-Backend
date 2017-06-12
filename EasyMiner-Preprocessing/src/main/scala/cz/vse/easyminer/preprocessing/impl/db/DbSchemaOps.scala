/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.preprocessing.impl.db

import cz.vse.easyminer.data.{SchemaOps => DataSchemaOps}
import cz.vse.easyminer.preprocessing.{SchemaOps => PreprocessingSchemaOps}

/**
  * Created by Vaclav Zeman on 29. 12. 2015.
  */

/**
  * Abstraction for creating database schema
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
