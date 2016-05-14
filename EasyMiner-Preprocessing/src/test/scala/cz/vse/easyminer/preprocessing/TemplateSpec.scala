package cz.vse.easyminer.preprocessing

import cz.vse.easyminer.core.util.Template
import org.fusesource.scalate.util.ResourceNotFoundException
import org.scalatest._

class TemplateSpec extends FlatSpec with TemplateOpt with Matchers {

  "Template" should "have defaultBasePath /cz/vse/easyminer/" in {
    Template.defaultBasePath should be("/cz/vse/easyminer/")
  }

  it should "throw exception if non-existent template" in {
    intercept[ResourceNotFoundException] {
      Template("nonExistentTemplate.mustache")
    }
  }

  it should "contain metadata scheme" in {
    Template("preprocessing/metadataSchema.mustache") should not be empty
  }

}

trait TemplateOpt {

  def template(name: String, attributes: Map[String, Any] = Map.empty) = Template(name, attributes)("/")

}
