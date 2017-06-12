/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data

import java.io.InputStream

/**
  * Created by Vaclav Zeman on 5. 9. 2015.
  */

/**
  * Preview parser parses an input stream and returns only small part of this stream as a sample of input data
  */
trait PreviewParser {

  def parse(is: InputStream): Array[Byte]

}

object PreviewParser {

  /**
    * This decorates some preview parser for supporing of compressions
    *
    * @param parser          preview parser
    * @param compressionType compression type
    * @return preview parser decorator
    */
  def apply(parser: PreviewParser)(compressionType: Option[CompressionType]): PreviewParser = new PreviewParser {
    def parse(is: InputStream): Array[Byte] = parser.parse(compressionType.map(CompressionType.decompressInputStream(is)).getOrElse(is))
  }

}