package cz.vse.easyminer.data

/**
 * Created by propan on 28. 1. 2016.
 */
trait ValueHistogramOps {

  def getHistogram(maxBins: Int, minValue: Option[IntervalBorder] = None, maxValue: Option[IntervalBorder] = None): Seq[ValueInterval]

}
