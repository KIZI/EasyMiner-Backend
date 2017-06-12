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

/**
  * This input stream restricts row size of an input document.
  * It is also prevention against DoS attack because very large rows can cause memory problem because some input format are parsed line by line and whole line is written into the memory.
  *
  * @param is          input stream
  * @param maxLineSize max line size in bytes
  */
class LineBoundedInputStream(is: InputStream, maxLineSize: Int) extends InputStream {

  val newline = 0x0A

  private var byteCounter = 0

  /**
    * If the maxLineSize is reached for a specific line then IndexOutOfBoundsException will be thrown
    *
    * @return byte
    */
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