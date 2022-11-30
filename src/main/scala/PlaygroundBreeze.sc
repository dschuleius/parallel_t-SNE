import breeze.linalg._
import breeze.numerics._
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.util.MLUtils
import org.apache.spark.mllib.rdd.MLPairRDDFunctions.fromPairRDD

case class VectorAndNorm(vector:DenseVector[Double], norm:Double)


val x = DenseMatrix.rand[Double](3,5)

// take rows of matrix and map to norm, as members of class VectorAndNorm:
val xArray = Array(Array(5.0, 4.8, 7.5, 10.0),
        Array(3.2, 2.1, 4.3, 2.8),
        Array(2.2, 1.1, 1.3, 2.3))
println(x)

val rowVecs = for ( i <- 0 until x.rows) yield x(i, ::)
rowVecs.getClass
val rowNorms = x(*, ::).map(rv => norm(rv, 2.0))
/*
val rowVecsWithNorms = rowVecs.zip(rowNorms.valuesIterator).map{
  case (rv, norm) => VectorAndNorm(rv.t, norm)
}
 */

def distance(xs: Array[Double], ys: Array[Double]): Double  = {
  sqrt((xs zip ys).map { case (x,y) => pow(y - x, 2) }.sum)
}
val testseq = 1 to 5
def combi(s : Seq[Int]) : Seq[(Int, Int)] =
  if(s.isEmpty)
    Seq()
  else
    s.tail.flatMap(x=> Seq(s.head -> x, x -> s.head)) ++ combi(s.tail)




def findNearestPoints(testPoints: Array[Array[Double]], trainPoints: Array[Array[Double]]): Array[Array[(Int, Double)]] = {
  testPoints.map { testInstance =>
    trainPoints.zipWithIndex.map { case (trainInstance, c) =>
      c -> distance(testInstance, trainInstance)
    }
  }
}

findNearestPoints(testPoints = xArray, trainPoints = xArray)

/*
val neighbors:Seq[Any] = rowVecsWithNorms.zipWithIndex().flatMap {
    case ((u, i), (v, j)) =>
      if(i < j) {
        val dist = norm(u - v)
        Seq((i, (j, dist)), (j, (i, dist)))
      } else Seq.empty
  }
  .topByKey(2)(Ordering.by(e => -e._2))
*/
