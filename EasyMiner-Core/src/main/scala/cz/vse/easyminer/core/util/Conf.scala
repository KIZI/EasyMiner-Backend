/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.core.util

import com.github.kxbmap.configs._
import com.typesafe.config.ConfigFactory

/**
  * This object return instance of configuration class, which is siple version of the TypeSafe config
  * In this Conf instance are all configuration attributes for a specific easyminer module
  */
object Conf {

  private val config = new Conf(new EnrichTypesafeConfig(ConfigFactory.load))

  def apply() = config

}

class Conf(etc: EnrichTypesafeConfig) {

  /**
    * Get value of same location
    *
    * @param path location in configuration
    * @tparam T result type of the value
    * @return value of the location
    */
  def get[T: AtPath](path: String): T = etc.get(path)

  /**
    * Same as get function, but it returns None if a location does not exist
    *
    * @param path location in configuration
    * @tparam T result type of the value
    * @return Some(x) x value of the location or None if the location does not exist
    */
  def opt[T: AtPath](path: String)(implicit cc: CatchCond = CatchCond.configException): Option[T] = etc.opt(path)

  /**
    * Same as get function, but with additional default value if a location does not exist
    *
    * @param path    location in configuration
    * @param default default value
    * @tparam T result type of the value
    * @return value of the location
    */
  def getOrElse[T: AtPath](path: String, default: => T)(implicit cc: CatchCond = CatchCond.missing): T = etc.getOrElse(path, default)

}
