/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data

/**
  * Created by Vaclav Zeman on 24. 8. 2015.
  */

/**
  * Abstraction for database schema
  */
trait SchemaOps {

  /**
    * It checks whether database schema exists
    *
    * @return true = schema exists
    */
  def schemaExists: Boolean

  /**
    * Create database schema
    */
  def createSchema(): Unit

}
