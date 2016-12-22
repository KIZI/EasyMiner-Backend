package cz.vse.easyminer.data.impl.db

import cz.vse.easyminer.core.db.MysqlDBConnector
import cz.vse.easyminer.data.impl.db.mysql.Tables.FieldTable
import cz.vse.easyminer.data._
import scalikejdbc._

/**
  * Created by propan on 9. 12. 2015.
  */
trait DbFieldBuilder extends FieldBuilder {

  val connector: MysqlDBConnector
  val dataSource: DataSourceDetail
  val fields: Seq[Field]

  import connector.DBConn

  def buildFields = {
    val cols = List(FieldTable.column.dataSource, FieldTable.column.name, FieldTable.column.`type`, FieldTable.column.uniqueValuesSize)
    DBConn localTx { implicit session =>
      sql"INSERT INTO ${FieldTable.table} ($cols) VALUES (?, ?, ?, ?)".batch(
        fields.map(field => Seq(
          dataSource.id,
          field.name,
          field.`type` match {
            case NominalFieldType => FieldTable.nominalName
            case NumericFieldType => FieldTable.numericName
          },
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
