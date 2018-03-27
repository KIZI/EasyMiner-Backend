package cz.vse.easyminer.miner.impl

import cz.vse.easyminer.miner.{OutlierWithInstance, OutliersTask}
import spray.json._

/**
  * Created by propan on 18. 8. 2015.
  */
object JsonFormatters extends DefaultJsonProtocol {

  object JsonOutliersTask {

    import cz.vse.easyminer.data.impl.JsonFormatters.JsonDate._

    implicit val JsonOutliersTaskFormat: RootJsonFormat[OutliersTask] = jsonFormat3(OutliersTask)

  }

  object JsonOutlierWithInstance {

    import cz.vse.easyminer.preprocessing.impl.JsonFormatters.JsonInstanceWithValue._

    implicit val JsonOutlierWithInstanceFormat: RootJsonFormat[OutlierWithInstance] = jsonFormat2(OutlierWithInstance)

  }

}