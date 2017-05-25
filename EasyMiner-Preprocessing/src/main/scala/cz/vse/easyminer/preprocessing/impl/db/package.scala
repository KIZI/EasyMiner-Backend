/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.preprocessing.impl

import cz.vse.easyminer.core.PersistentLock
import cz.vse.easyminer.data.impl.db.mysql.Tables.LockTable
import scalikejdbc._

/**
 * Created by Vaclav Zeman on 17. 2. 2016.
 */
package object db {

  implicit val lockTable: SQLSyntaxSupport[PersistentLock] = LockTable

}
