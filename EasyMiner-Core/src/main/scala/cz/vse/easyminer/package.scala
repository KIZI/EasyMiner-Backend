package cz.vse

import akka.actor.ActorSystem

/**
 * Created by propan on 27. 2. 2016.
 */
package object easyminer {

  implicit lazy val actorSystem = ActorSystem("easyminer-actor-system")

}
