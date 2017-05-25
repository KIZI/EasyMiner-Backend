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
trait PreviewParser {

  def parse(is: InputStream): Array[Byte]

}

object PreviewParser {

  def apply(parser: PreviewParser)(compressionType: Option[CompressionType]): PreviewParser = new PreviewParser {
    def parse(is: InputStream): Array[Byte] = parser.parse(compressionType.map(CompressionType.decompressInputStream(is)).getOrElse(is))
  }

}