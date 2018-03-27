package cz.vse.easyminer.miner.impl.r

import cz.vse.easyminer.miner.RConnectionInit
import org.rosuda.REngine.Rserve.RConnection

/**
  * Created by propan on 28. 2. 2017.
  */
object RConnectionInits {

  trait MinerRConnectionInit extends RConnectionInit {
    def init(rConnection: RConnection): Unit = {
      rConnection.eval("library(RJDBC)")
      rConnection.eval("library(arules)")
      rConnection.eval("library(rCBA)")
    }
  }

  trait OutlierDetectionRConnectionInit extends RConnectionInit {
    def init(rConnection: RConnection): Unit = {
      rConnection.eval("library(RJDBC)")
      rConnection.eval("library(fpmoutliers)")
    }
  }

}
