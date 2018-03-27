package cz.vse.easyminer.preprocessing.impl.db

import cz.vse.easyminer.preprocessing.{EquidistantIntervalsAttribute, EquifrequentIntervalsAttribute, EquisizedIntervalsAttribute}
import eu.easyminer.discretization
import eu.easyminer.discretization.algorithm.Discretization
import eu.easyminer.discretization.task.{EquidistanceDiscretizationTask, EquifrequencyDiscretizationTask, EquisizeDiscretizationTask}
import eu.easyminer.discretization.{DiscretizationTask, Support}

import scala.language.implicitConversions

/**
  * Created by propan on 11. 4. 2017.
  */
object AttributeConversions {

  private trait BufferedDiscretizationTask extends DiscretizationTask {
    def getBufferSize: Int = 1000000
  }

  implicit def equidistantIntervalsAttributeToDiscretization(equidistantIntervalsAttribute: EquidistantIntervalsAttribute): Discretization[Double] = Discretization(
    new EquidistanceDiscretizationTask with BufferedDiscretizationTask {
      def getNumberOfBins: Int = equidistantIntervalsAttribute.bins
    }
  )

  implicit def equifrequentIntervalsAttributeToDiscretization(equifrequentIntervalsAttribute: EquifrequentIntervalsAttribute): Discretization[Double] = Discretization(
    new EquifrequencyDiscretizationTask with BufferedDiscretizationTask {
      def getNumberOfBins: Int = equifrequentIntervalsAttribute.bins
    }
  )

  implicit def equisizedIntervalsAttributeToDiscretization(equisizedIntervalsAttribute: EquisizedIntervalsAttribute): Discretization[Double] = Discretization(
    new EquisizeDiscretizationTask with BufferedDiscretizationTask {
      def getMinSupport: Support = new discretization.RelativeSupport(equisizedIntervalsAttribute.support)
    }
  )

}
