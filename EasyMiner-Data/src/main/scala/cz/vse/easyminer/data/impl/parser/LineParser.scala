/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data.impl.parser

import java.io.InputStream

import cz.vse.easyminer.core.Validator
import cz.vse.easyminer.core.util.BasicFunction._
import cz.vse.easyminer.data.PreviewParser
import cz.vse.easyminer.core.util.BasicValidators.Greater

import scala.collection.mutable.ArrayBuffer

/**
 * Created by Vaclav Zeman on 5. 9. 2015.
 */
class LineParser private(maxLines: Int,
                         maxBufferSize: Int) extends PreviewParser {

  val maxLineSize = (maxBufferSize.toDouble / maxLines).toInt

  def parse(is: InputStream): Array[Byte] = {
    val arrayBuffer = ArrayBuffer.empty[Byte]
    var numberOfLines = 0
    tryClose(new LineBoundedInputStream(is, maxLineSize)) { nis =>
      Stream.continually(nis.read()).takeWhile(_ != -1 && numberOfLines < maxLines).foreach { byte =>
        arrayBuffer += byte.toByte
        if (byte == nis.newline) {
          numberOfLines = numberOfLines + 1
        }
      }
      arrayBuffer.toArray
    }
  }


}

object LineParser {

  def apply(maxLines: Int, maxBufferSize: Int = 1000000) = {
    Validator(maxLines)(Greater(0))
    PreviewParser(new LineParser(maxLines, maxBufferSize)) _
  }

}