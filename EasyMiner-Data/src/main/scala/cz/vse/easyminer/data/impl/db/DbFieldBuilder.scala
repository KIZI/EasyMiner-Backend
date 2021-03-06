/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data.impl.db

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.data.impl.db.mysql.Tables.FieldTable
import cz.vse.easyminer.data._
import scalikejdbc._

/**
  * Created by Vaclav Zeman on 9. 12. 2015.
  */

/**
  * This is base for field builder.
  * For all field builders we save field information into mysql database
  */
trait DbFieldBuilder extends FieldBuilder {

  val connector: MysqlDBConnector
  val dataSource: DataSourceDetail
  val fields: Seq[Field]

  import connector.DBConn

  def buildFields = {
    val cols = FieldTable.column.columns.filter(_ != FieldTable.column.id)
    DBConn localTx { implicit session =>
      sql"INSERT INTO ${FieldTable.table} ($cols) VALUES (?, ?, ?, ?, ?, ?, ?)".batch(
        fields.map(field => Seq(
          dataSource.id,
          field.name,
          field.`type` match {
            case NominalFieldType => FieldTable.nominalName
            case NumericFieldType => FieldTable.numericName
          },
          0,
          0,
          0,
          0
        )): _*
      ).apply()
    }
    val fieldsDetail = DBConn readOnly { implicit session =>
      val a = FieldTable.syntax("a")
      sql"SELECT ${a.result.*} FROM ${FieldTable as a} WHERE ${a.dataSource} = ${dataSource.id} ORDER BY ${a.id}"
        .map(FieldTable(a.resultName))
        .list()
        .apply()
    }
    fieldsDetail
  }

}
