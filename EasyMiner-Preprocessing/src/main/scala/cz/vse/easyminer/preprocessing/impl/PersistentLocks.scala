/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.preprocessing.impl

/**
 * Created by Vaclav Zeman on 17. 2. 2016.
 */
object PersistentLocks {

  def datasetLockName(datasetId: Int) = "dataset-" + datasetId

}
