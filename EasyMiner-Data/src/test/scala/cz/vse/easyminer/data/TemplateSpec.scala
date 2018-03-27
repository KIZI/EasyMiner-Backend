package cz.vse.easyminer.data

import cz.vse.easyminer.core.util.Template
import org.fusesource.scalate.util.ResourceNotFoundException
import org.scalatest.{FlatSpec, Matchers}

/**
 * Created by propan on 4. 2. 2016.
 */
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
    Template("data/metadataSchema.mustache") should not be empty
  }

}
