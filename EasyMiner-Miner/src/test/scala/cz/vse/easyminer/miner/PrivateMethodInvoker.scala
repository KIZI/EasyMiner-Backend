package cz.vse.easyminer.miner

import scala.language.implicitConversions

/**
  * Created by propan on 15. 3. 2017.
  */
trait PrivateMethodInvoker {

  class MethodInvoker(obj: AnyRef) {

    def invokePrivate[T](methodName: String)(args: AnyRef*)(implicit ctag: reflect.ClassTag[T]) = ctag.runtimeClass.asInstanceOf[Class[T]].getDeclaredMethods
      .filter(method => method.getName == methodName || method.getName.endsWith("$$" + methodName))
      .find(method => method.getParameterCount == args.length && method.getParameterTypes.zip(args.map(_.getClass)).forall(x => x._1 == x._2))
      .get
      .invoke(obj, args: _*)

  }

  implicit def anyRefToMethodInvoker(obj: AnyRef): MethodInvoker = new MethodInvoker(obj)

}
