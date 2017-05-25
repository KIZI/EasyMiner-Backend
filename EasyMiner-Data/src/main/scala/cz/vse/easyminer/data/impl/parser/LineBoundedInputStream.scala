/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data.impl.parser

import java.io.InputStream

import cz.vse.easyminer.core.StatusCodeException.BadRequest

/**
 * Created by Vaclav Zeman on 5. 9. 2015.
 */
class LineBoundedInputStream(is: InputStream, maxLineSize: Int) extends InputStream {

  val newline = 0x0A

  private var byteCounter = 0

  def read(): Int = {
    val byte = is.read()
    if (byte == -1) {
      -1
    } else if (byte == newline) {
      byteCounter = 0
      byte
    } else {
      byteCounter = byteCounter + 1
      if (byteCounter > maxLineSize) throw new IndexOutOfBoundsException("Line is too large.") with BadRequest
      byte
    }
  }

}