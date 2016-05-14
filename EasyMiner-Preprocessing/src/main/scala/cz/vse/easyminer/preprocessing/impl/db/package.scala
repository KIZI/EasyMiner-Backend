package cz.vse.easyminer.preprocessing.impl

import cz.vse.easyminer.core.PersistentLock
import cz.vse.easyminer.data.impl.db.mysql.Tables.LockTable
import scalikejdbc._

/**
 * Created by propan on 17. 2. 2016.
 */
package object db {

  implicit val lockTable: SQLSyntaxSupport[PersistentLock] = LockTable

}
