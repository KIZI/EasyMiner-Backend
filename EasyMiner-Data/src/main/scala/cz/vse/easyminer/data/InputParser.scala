/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data

import java.io.InputStream

/**
  * Created by Vaclav Zeman on 26. 7. 2015.
  */

/**
  * Abstraction for parsing (uploading) an input data source (with a specific format)
  * This parser creates data source from an input stream and uses a specific data source builder
  */
trait InputParser {

  val dataSourceBuilder: DataSourceBuilder

  /**
    * Parse input stream and use result for creating of a data source by the data source builder
    *
    * @param is input stream
    * @return created data source detail
    */
  def write(is: InputStream): DataSourceDetail

}