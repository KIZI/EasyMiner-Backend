package cz.vse.easyminer.data

import java.io.InputStream

/**
 * Created by propan on 26. 7. 2015.
 */
trait InputParser {

  val dataSourceBuilder: DataSourceBuilder

  def write(is: InputStream): DataSourceDetail

}