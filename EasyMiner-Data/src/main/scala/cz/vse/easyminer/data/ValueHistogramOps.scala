/*
 * @author Vaclav Zeman
 * @license http://www.apache.org/licenses/LICENSE-2.0 Apache License, Version 2.0
 * @link http://easyminer.eu
 */

package cz.vse.easyminer.data

/**
  * Created by Vaclav Zeman on 28. 1. 2016.
  */

/**
  * Abstraction for value operations related to histograms
  */
trait ValueHistogramOps {

  /**
    * For some data source and field this function return aggregated values/instances histogram by number of bins.
    *
    * @param maxBins  maximal number of bins
    * @param minValue minimal value of the histogram
    * @param maxValue maximal value of the histogram
    * @return intervals which represents a histogram
    */
  def getHistogram(maxBins: Int, minValue: Option[IntervalBorder] = None, maxValue: Option[IntervalBorder] = None): Seq[ValueInterval]

}
