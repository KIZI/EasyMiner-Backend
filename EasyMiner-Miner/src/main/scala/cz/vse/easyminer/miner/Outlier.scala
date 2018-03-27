package cz.vse.easyminer.miner

import java.util.Date

import cz.vse.easyminer.preprocessing.InstanceWithValue

/**
  * Created by propan on 28. 2. 2017.
  */
case class Outlier(id: Int, score: Double)

case class OutlierWithInstance(score: Double, instance: InstanceWithValue)

case class OutliersTask(id: Int, time: Date, dataset: Int)