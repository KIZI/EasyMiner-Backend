package cz.vse.easyminer.data

import org.scalatest.{DoNotDiscover, FlatSpec, Matchers}

/**
 * Created by propan on 26. 9. 2015.
 */
@DoNotDiscover
class MainServiceSpec(restSpec: RestSpec) extends FlatSpec with Matchers {

  import restSpec._

  "Main Service" should "return 404 in the root path" in {
    Get() ~> route ~> check {
      response.status.intValue shouldBe 404
    }
  }

  it should "return 401 without apikey and 404 if authorized" in {
    Get("/api/v1") ~> route ~> check {
      response.status.intValue shouldBe 401
    }
    authorizedRequest(Get("/api/v1")) ~> route ~> check {
      response.status.intValue shouldBe 404
    }
  }

}
