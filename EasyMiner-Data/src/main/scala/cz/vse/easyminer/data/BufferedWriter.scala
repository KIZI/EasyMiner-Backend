package cz.vse.easyminer.data

/**
 * Created by propan on 4. 8. 2015.
 */
trait BufferedWriter {

  def write(bytes: Array[Byte]): Boolean

  def finish(): Unit

}
