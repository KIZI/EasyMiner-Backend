package cz.vse.easyminer.miner

import cz.vse.easyminer.miner.impl.RConnectionPoolImpl

/**
 * Created by propan on 27. 2. 2016.
 */
package object rest {

  implicit lazy val rConnectionPool = RConnectionPoolImpl.default

}
