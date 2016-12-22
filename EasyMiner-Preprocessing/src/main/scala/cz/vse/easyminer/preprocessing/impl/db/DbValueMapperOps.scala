package cz.vse.easyminer.preprocessing.impl.db

import cz.vse.easyminer.data.{NominalValue, NullValue, NumericValue, Value}
import cz.vse.easyminer.preprocessing.ValueMapperOps.{ItemMapper, ValueMapper}
import cz.vse.easyminer.preprocessing._
import scalikejdbc._

import scala.language.implicitConversions

/**
  * Created by propan on 13. 2. 2016.
  */
trait DbValueMapperOps extends ValueMapperOps {

  type Values[A] = Map[AttributeDetail, Set[A]]
  type MapperKey[A] = (AttributeDetail, A)

  private val bucketSize = 100

  protected[this] val valueDetailTable: SQLSyntaxSupport[ValueDetail]

  protected[this] def useDbSession[T](f: DBSession => T): T

  /**
    * This separates the enter map with seq values to smaller buckets where each bucket has maximal $bucketSize values.
    * This is useful when we have many values to map and we need to separate the one big database query to many smaller queries.
    * This operation is lazy
    *
    * @param map input values which will be mapped
    * @tparam A key (it will be AttributeDetail)
    * @tparam B value (Value or NormalizedValue)
    * @return iterator of maps where each map has maximal $bucketSize values
    */
  private def bucketedValues[A, B](map: Map[A, Set[B]]): Iterator[Map[A, Set[B]]] = map.toIterator.flatMap {
    case (key, seq) => seq.map(key -> _)
  }.grouped(bucketSize).map(_.foldLeft(Map.empty[A, Set[B]]) { case (result, (k, v)) =>
    result + (k -> (result.getOrElse(k, Set.empty) + v))
  })

  /**
    * This maps normalized values to where query to find original values
    *
    * @param normalizedValues Map[ AttributeDetail, Seq[NormalizedValue] ]
    * @return SQLSyntax
    */
  implicit private def buildValuesWhereFetchQuery(normalizedValues: Values[Int]): SQLSyntax = normalizedValues.map { case (attributeDetail, normalizedValueSeq) =>
    sqls"${valueDetailTable.column.attribute} = ${attributeDetail.id} AND ${valueDetailTable.column.id} IN ($normalizedValueSeq)"
  }.reduce(_ or _)

  /**
    * This maps values to where query to find normalized values
    *
    * @param values Map[ AttributeDetail, Seq[Value] ]
    * @return SQLSyntax
    */
  implicit private def buildNormalizedValuesWhereFetchQuery(values: Values[Value]): SQLSyntax = values.map { case (attributeDetail, valueSeq) =>
    val preparedValues = valueSeq.collect {
      case NominalValue(value) => Some(value)
      case NumericValue(original, _) => Some(original)
    }
    sqls"${valueDetailTable.column.attribute} = ${attributeDetail.id} AND (${valueDetailTable.column.valueNominal} IN ($preparedValues) OR ${valueDetailTable.column.valueNominal} IS NULL)"
  }.reduce(_ or _)

  /**
    * This method executes a database query to find values within one bucket.
    * The result of the query is a list of value details with all information about fetched values (id, text values, frequencies, etc.)
    *
    * @param whereSyntax  where cause - there is the complete list of values which we want to find
    * @param dBSession    database connect
    * @param attributeMap mapper attribute id to attribute detail
    * @return list of value details
    */
  private def fetchValueDetails(whereSyntax: SQLSyntax)(implicit dBSession: DBSession, attributeMap: Map[Int, AttributeDetail]) = {
    sql"SELECT ${valueDetailTable.column.*} FROM ${valueDetailTable.table} WHERE $whereSyntax".map { wrs =>
      val id = wrs.int(valueDetailTable.column.id)
      val attribute = attributeMap(wrs.int(valueDetailTable.column.attribute))
      val frequency = wrs.int(valueDetailTable.column.frequency)
      val value: Option[ValueDetail] = attribute.`type` match {
        case NominalAttributeType => wrs.stringOpt(valueDetailTable.column.valueNominal).map(value => NominalValueDetail(id, attribute.id, value, frequency))
        case NumericAttributeType => wrs.doubleOpt(valueDetailTable.column.valueNumeric).map(value => NumericValueDetail(id, attribute.id, wrs.string(valueDetailTable.column.valueNominal), value, frequency))
      }
      value.getOrElse(NullValueDetail(id, attribute.id, frequency))
    }.list().apply()
  }

  /**
    * Trait of an mapper pair builder.
    * The builder contains one method for creation a pair for a particular mapper which maps one value type to another values type (e.g. Value -> NormalizedValue, NormalizedValue -> Value)
    *
    * @tparam A mapper key
    * @tparam B mapper value
    */
  private trait MapperPairBuilder[A, B] {
    def buildMapperItem(attributeDetail: AttributeDetail, value: Value, normalizedValue: Int): (A, B)
  }

  implicit private object ValueMapperPairBuilder extends MapperPairBuilder[MapperKey[Value], Int] {
    def buildMapperItem(attributeDetail: AttributeDetail, value: Value, normalizedValue: Int): (MapperKey[Value], Int) = (attributeDetail, value) -> normalizedValue
  }

  implicit private object NormalizedValueMapperPairBuilder extends MapperPairBuilder[MapperKey[Int], Value] {
    def buildMapperItem(attributeDetail: AttributeDetail, value: Value, normalizedValue: Int): (MapperKey[Int], Value) = (attributeDetail, normalizedValue) -> value
  }

  /**
    * This method uses some MapperPairBuilder for building of a mapper key-value pair from a value detail.
    *
    * @param valueDetail       ValueDetail
    * @param attributeMap      mapper attribute id to attribute detail
    * @param mapperPairBuilder MapperPairBuilder[A, B]
    * @tparam A mapper key
    * @tparam B mapper value
    * @return key-value pair for a mapper
    */
  private def valueDetailToMapperPair[A, B](valueDetail: ValueDetail)(implicit attributeMap: Map[Int, AttributeDetail], mapperPairBuilder: MapperPairBuilder[A, B]): (A, B) = {
    val attributeDetail = attributeMap(valueDetail.attribute)
    val value = valueDetail match {
      case v: NominalValueDetail => NominalValue(v.value)
      case v: NumericValueDetail => NumericValue(v.original, v.value)
      case _: NullValueDetail => NullValue
    }
    val normalizedValue = valueDetail.id
    mapperPairBuilder.buildMapperItem(attributeDetail, value, normalizedValue)
  }

  /**
    * This class is ValueMapper which uses in-memory loaded mapper to map a value to a normalized value
    *
    * @param mapper loaded mapper
    */
  class LoadedValueMapper(mapper: Map[MapperKey[Value], Int]) extends ValueMapper {
    def item(attributeDetail: AttributeDetail, value: Value): Option[Int] = mapper.get(attributeDetail -> value)
  }

  /**
    * This class is NormalizedValueMapper which uses in-memory loaded mapper to map a normalized value to a value
    *
    * @param mapper loaded mapper
    */
  class LoadedNormalizedValueMapper(mapper: Map[MapperKey[Int], Value]) extends ItemMapper {
    def value(attributeDetail: AttributeDetail, normalizedValue: Int): Option[Value] = mapper.get(attributeDetail -> normalizedValue)
  }

  /**
    * This method builds mapper which maps all input values within the Map structure to normalized values or original values
    *
    * @param values               input values which has to be mapped (seq of Value or NormalizedValue)
    * @param buildWhereFetchQuery method which builds input values to a database query where cause
    * @param itemMapperBuilder    object which builds pairs for the final mapper
    * @tparam A mapper key
    * @tparam B mapper value
    * @return in-memory loaded mapper
    */
  private def buildMapper[A, B](values: Values[A])(implicit buildWhereFetchQuery: Values[A] => SQLSyntax, itemMapperBuilder: MapperPairBuilder[MapperKey[A], B]) = useDbSession { implicit session =>
    implicit val attributeMap = values.keys.map(attribute => attribute.id -> attribute).toMap
    // Step 1: divide values to same size buckets
    // Step 2: each bucket is map to a where cause for a future database query
    // Step 3: each where cause is use for fetching a list of value detail from a database - all value details are merged across all buckets (flatMap)
    // Step 4: each value detail is map to a key-value pair (MapperKey[NormalizedValue] -> Value or MapperKey[Value] -> NormalizedValue)
    // Step 5: one Map instance is created by the seq o key-value pairs - the final mapper is created
    bucketedValues(values).map[SQLSyntax](x => x).flatMap(fetchValueDetails).map(valueDetailToMapperPair[MapperKey[A], B]).toMap
  }

  def valueMapper(values: Values[Value]): ValueMapper = new LoadedValueMapper(buildMapper(values))

  def itemMapper(normalizedValues: Values[Int]): ItemMapper = new LoadedNormalizedValueMapper(buildMapper(normalizedValues))

}
