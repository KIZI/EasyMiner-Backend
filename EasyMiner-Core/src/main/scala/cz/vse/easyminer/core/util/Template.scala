/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.core.util

import java.io.{PrintWriter, StringWriter}
import java.text.DecimalFormat

import org.fusesource.scalate.{DefaultRenderContext, TemplateEngine}

object Template {

  val defaultBasePath: String = "/cz/vse/easyminer/"

  private val df = {
    val df = new DecimalFormat
    df.setGroupingUsed(false)
    df.setMaximumFractionDigits(6)
    df
  }

  private val engine = new TemplateEngine

  def apply(name: String, attributes: Map[String, Any] = Map.empty)(implicit basePath: String = defaultBasePath): String = {
    import BasicFunction.tryClose
    val template = engine.load(basePath + name)
    tryClose(new StringWriter())(buffer => {
      val context = new DefaultRenderContext(basePath + name, engine, new PrintWriter(buffer))
      for ((k, v) <- attributes)
        context.attributes(k) = v
      context.numberFormat = df
      template.render(context)
      buffer.toString
    })
  }

}