/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse

import akka.actor.ActorSystem

/**
 * Created by Vaclav Zeman on 27. 2. 2016.
 */
package object easyminer {

  implicit lazy val actorSystem = ActorSystem("easyminer-actor-system")

}
