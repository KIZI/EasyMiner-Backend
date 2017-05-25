/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.core.util

import com.github.kxbmap.configs._
import com.typesafe.config.ConfigFactory

object Conf {

  private val config = new Conf(new EnrichTypesafeConfig(ConfigFactory.load))

  def apply() = config
  
}

class Conf(etc: EnrichTypesafeConfig) {
  
  def get[T: AtPath](path: String): T = etc.get(path)

  def opt[T: AtPath](path: String)(implicit cc: CatchCond = CatchCond.configException): Option[T] = etc.opt(path)

  def getOrElse[T: AtPath](path: String, default: => T)(implicit cc: CatchCond = CatchCond.missing): T = etc.getOrElse(path, default)
  
}
