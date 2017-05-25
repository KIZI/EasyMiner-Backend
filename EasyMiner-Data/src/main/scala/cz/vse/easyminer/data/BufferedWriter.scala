/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data

/**
 * Created by Vaclav Zeman on 4. 8. 2015.
 */
trait BufferedWriter {

  def write(bytes: Array[Byte]): Boolean

  def finish(): Unit

}
