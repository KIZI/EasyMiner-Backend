package cz.vse.easyminer.data

import cz.vse.easyminer.core.util.Template

/**
 * Created by propan on 4. 2. 2016.
 */
trait TemplateOpt {

  def template(name: String, attributes: Map[String, Any] = Map.empty) = Template(name, attributes)("/")

}
