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
trait InputParser {

  val dataSourceBuilder: DataSourceBuilder

  def write(is: InputStream): DataSourceDetail

}