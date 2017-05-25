/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.miner

import cz.vse.easyminer.miner.impl.RConnectionPoolImpl

/**
 * Created by Vaclav Zeman on 27. 2. 2016.
 */
package object rest {

  implicit lazy val rConnectionPool = RConnectionPoolImpl.default

}
