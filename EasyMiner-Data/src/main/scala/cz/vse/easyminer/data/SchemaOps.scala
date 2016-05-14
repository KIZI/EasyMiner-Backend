package cz.vse.easyminer.data

/**
 * Created by propan on 24. 8. 2015.
 */
trait SchemaOps {

  def schemaExists: Boolean

  def createSchema(): Unit

}
