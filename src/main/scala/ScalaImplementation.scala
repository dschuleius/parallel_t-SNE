// https://spark.apache.org/docs/latest/rdd-programming-guide.html
// Create package
import org.apache.spark.SparkContext
import org.apache.spark.SparkConf
import org.apache.spark.ml.feature.PCA
import org.apache.spark.mllib._
import breeze.util.JavaArrayOps.dmToArray2

import scala.io.Source
import breeze.linalg._
import breeze.storage._
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.linalg.distributed.RowMatrix
import org.apache.spark.rdd.RDD

object ScalaImplementation {

  // set up Spark, changing to local host.
  val conf = new SparkConf()
    .setAppName("distributed_t-SNE")
    .setMaster("local[*]")
    .set("spark.driver.host", "127.0.0.1")
    .set("spark.driver.bindAddress", "127.0.0.1")
  val sc = new SparkContext(conf)
  // Show only Error and not Info messages
  sc.setLogLevel("ERROR")

  // function that imports MNIST from .txt files.
  def importData(fileName: String, sampleSize: Int): Array[Array[Double]] = {
    // read the file and split it into lines
    val lines = Source.fromFile(fileName).getLines.take(sampleSize).toArray
    Source.fromFile(fileName).close()

    // split each line into fields and convert the fields to doubles
    // trim removes leading and trailing blank space from each field
    val data = lines.map(_.trim.split("\\s+").map(_.toDouble))



    // return the data as an array of arrays of doubles
    data
  }

  val sampleSize: Int = 100
  // calling sc.parallelize to create 2 RDDs from textfile.
  // relative path does not work, probably problem with SBT folder structure
  val toRDDTime = System.nanoTime()
//  val MNISTlabels = sc.parallelize(importData("/Users/anani/Code/parallel_t-SNE/data/mnist2500_labels.txt", sampleSize))
  val MNISTdata: RDD[Array[Double]] = sc.parallelize(importData("/Users/anani/Code/parallel_t-SNE/data/mnist2500_X.txt", sampleSize))
  println("To RDD time for " + sampleSize + " samples: " + (System.nanoTime - toRDDTime)/1000000 + "ms")

  // testing
//  MNISTdata.take(10).foreach(println)

  def euclideanDistance(point1: Array[Double], point2: Array[Double]): Double = {
    // Calculate the squared Euclidean distance between the two points
    val squaredDistance = point1.zip(point2).map { case (x, y) => (x - y) * (x - y) }.sum

    // Return the Euclidean distance
    Math.sqrt(squaredDistance)
  }

  // cartesian function very expensive on big data sets, as new RDD with all possible combinations is created.
  def pairwiseDistances(points: RDD[Array[Double]]): RDD[((Int, Int), Double)] = {
    val indexedPoints = points.zipWithIndex().map { case (point, index) => (index, point) }
    val pointPairs = indexedPoints.cartesian(indexedPoints)
    pointPairs.map { case ((index1, point1), (index2, point2)) =>
      ((index1.toInt, index2.toInt), euclideanDistance(point1, point2))
    }
  }

//  pairwiseDistances(MNISTdata).take(5).foreach(println)

  // !! still to do: perplexity calculation instead of constant sigma !!
  def computeSimilarityScoresGauss(distances: RDD[((Int, Int), Double)],
                                   sigma: Double,
                                   n: Int = sampleSize): RDD[((Int, Int), Double)] = {

//    val n = distances.count().toInt
    val unnormSimilarities = distances.map { case ((i, j), d) =>
      ((i, j), math.exp(-1 * scala.math.pow(d, 2) / (2 * scala.math.pow(sigma, 2))))
    }

    val denominators = unnormSimilarities.filter { case ((i, k), _) => i != k }.map { case ((i, j), d) =>
      ((i, j), math.exp(-1 * scala.math.pow(d, 2) / (2 * scala.math.pow(sigma, 2))))}.reduceByKey(_ + _)

    val unnormSimilaritiesWithDenominator = unnormSimilarities.join(denominators).map { case ((i, j), (unnorm, denominator)) =>
      ((i, j), unnorm / denominator) }

    val flippedUnnormSimWithDenom = unnormSimilaritiesWithDenominator.map(x => (x._1.swap, x._2))
    val joinedUnnormSimWithDenom = unnormSimilaritiesWithDenominator.join(flippedUnnormSimWithDenom)
    val normSimilarities = joinedUnnormSimWithDenom.mapValues { case (s1, s2) => (s1 + s2) / (2 * n) }


    normSimilarities
    }



  // testing computeSimilarityScoreGauss
//  computeSimilarityScoresGauss(pairwiseDistances(MNISTdata), sigma = 1).take(10).foreach(println)

  // returns a tuple of 2 RDDs, "num" containing the numerator values, which are later needed for the gradient
  // computation, "normSimilarities" containing the Similarity Scores in the low-dim. representation.
  def computeSimilarityScoresT(distances: RDD[((Int, Int), Double)], n: Int = sampleSize): (RDD[((Int, Int), Double)], RDD[((Int, Int), Double)]) = {

//    val n = distances.count().toInt
    val num = distances.map { case ((i, j), d) =>
      ((i, j), (1.0 / (1 + scala.math.pow(d, 2))))
    }

    val denominators = num.filter { case ((i, k), _) => i != k }.map { case ((i, j), d) =>
      ((i, j), (1.0 / 1.0 + scala.math.pow(d, 2)))
    }.reduceByKey(_ + _)

    val unnormSimilaritiesWithDenominator = num.join(denominators).map { case ((i, j), (unnorm, denominator)) =>
      ((i, j), unnorm / denominator)
    }

    val flippedUnnormSimWithDenom = unnormSimilaritiesWithDenominator.map(x => (x._1.swap, x._2))
    val joinedUnnormSimWithDenom = unnormSimilaritiesWithDenominator.join(flippedUnnormSimWithDenom)
    val normSimilarities = joinedUnnormSimWithDenom.mapValues { case (s1, s2) => (s1 + s2) / (2 * n) }


    (normSimilarities, num)
  }

  // testing computeSimilarityScoresT
//  computeSimilarityScoresT(pairwiseDistances(MNISTdata))._1.take(10).foreach(println)


  /*
  def sortColumns(matrix: DenseMatrix[Double], vector: DenseVector[Double]): DenseMatrix[Double] = {
    // sort Array in descending order
    val sortedVector = vector.toArray.sortWith(_ > _)
    val sortedMatrix = DenseMatrix.zeros[Double](matrix.rows, matrix.cols)
    for (i <- 0 until matrix.cols) {
      val colIndex = vector.findAll(_ == sortedVector(i)).head
      sortedMatrix(::, i) := matrix(::, colIndex)
    }
    sortedMatrix
  }


  // stick to builtin PCA function, this is just backup

  def initialPCA(data: RDD[Array[Double]], k: Int = 2): RDD[Array[Double]] = {
    // assert non-empty RDD and no empty rows

    // Convert data to breeze DenseMatrix
    val dataMatrix = DenseMatrix(data.map(row => DenseVector(row)).collect(): _*)


    // Calculate column mean as vector of sum of columns multiplied by 1/#rows
    // Element-wise division is not implemented as it seems, so use mult. by inverse.
    // Subtract column means from respective column entries in dataMatrix
    val meanVector = sum(dataMatrix(::, *)) *:* (1.0 / dataMatrix.rows.toDouble)
    val centeredDataMatrix = dataMatrix(*, ::).map(row => (row - meanVector.t))

      // Compute covariance matrix (symmetric).
      val covMatrix = breeze.linalg.cov(centeredDataMatrix)

      // Compute eigenvalues and eigenvectors of covariance matrix.
      val es = eigSym(covMatrix)

    // Sort eigenvalues and eigenvectors in descending order.
    val sortedEigenVectors = sortColumns(es.eigenvectors, es.eigenvalues)

    // Project data onto top k eigenvectors (change-of-basis).
    // choose top k eigenvectors
    val topEigenVectors = sortedEigenVectors(::, 0 until k)
    val projectedData = (topEigenVectors.t * centeredDataMatrix.t).t

    // Convert projected data back to Array[Array[Double]]
    val projDataRDD = sc.parallelize(dmToArray2(projectedData))

    projDataRDD
  }

   */


  // built-in PCA function
  def mlPCA(data: RDD[Array[Double]], k: Int = 2): RDD[Array[Double]] = {
    val rows = data.map(x => Vectors.dense(x))
    val mat: RowMatrix = new RowMatrix(rows)

    // Compute the top 4 principal components.
    // Principal components are stored in a local dense matrix.
    val pc: linalg.Matrix = mat.computePrincipalComponents(k)

    // Project the rows to the linear space spanned by the top 4 principal components.
    val projected: RowMatrix = mat.multiply(pc)

    val projectedRDD: RDD[Array[Double]] = projected.rows.map(_.toArray)

    projectedRDD
  }
//  println("Testing builtin PCA function:")
//  mlPCA(MNISTdata).foreach(arr => println(arr.mkString(",")))


  def stackVector(vector: DenseVector[Double], n: Int): DenseMatrix[Double] = {
    DenseMatrix.tabulate[Double](n, vector.size)((i, j) => vector(j))
  }



  // GD optimization is inherently sequential, hence we use a DenseMatrix collection to handle the data.
  def tSNEsimple(X: RDD[Array[Double]],
                 P: RDD[((Int, Int), Double)],
                 Q: RDD[((Int, Int), Double)],
                 num: RDD[((Int, Int), Double)],
                 k: Int = 2,
                 max_iter: Int = 1000,
                 initial_momentum: Double = 0.5,
                 final_momentum: Double = 0.8,
                 min_gain: Double = 0.01,
                 lr: Double = 500,
                 sampleSize: Int = ScalaImplementation.sampleSize):

  DenseMatrix[Double] = {

//    assert(P.map(_._1._1).max() + 1 == Q.map(_._1._1).max() + 1, "SimilarityScore multi-dim. Arrays must have the same number of rows.")
//    assert(P.map(_._1._2).max() + 1 == Q.map(_._1._2).max() + 1, "SimilarityScore multi-dim. Arrays must have the same number of columns.")
//    assert(P.map(_._1._1).max() + 1 == P.map(_._1._2).max() + 1  && Q.map(_._1._1).max() + 1 == Q.map(_._1._2).max() + 1, "SimilarityScore multi-dim. Arrays must be symmetric.")

    // initialize variables
    val initVarTime = System.nanoTime()
    val n: Int = sampleSize
    val dCdY = DenseMatrix.zeros[Double](sampleSize, k)
    val iY = DenseMatrix.zeros[Double](sampleSize, k)
    val gains = DenseMatrix.ones[Double](sampleSize, k)
    val Ymat = new DenseMatrix[Double](sampleSize, k, mlPCA(X).collect().flatten) // compute SimilarityScores in low dim:
    println("initVarTime time: " + (System.nanoTime - initVarTime) / 1000000 + "ms")

    val pMatCollTime = System.nanoTime()
    val PmatArray = P.map { case ((i, j), v) => (i, j, v) }.collect()
    println("pMatCollTime time collect: " + (System.nanoTime - pMatCollTime) / 1000000 + "ms")

    val Pmat: DenseMatrix[Double] = DenseMatrix.tabulate(sampleSize, sampleSize)((i, j) => PmatArray.find(x => x._1 == i && x._2 == j).map(_._3).getOrElse(0.0))
    println("pMatCollTime time find: " + (System.nanoTime - pMatCollTime) / 1000000 + "ms")

    val qMatCollTime = System.nanoTime()
    val QmatArray = Q.map { case ((i, j), v) => (i, j, v) }.collect()
    val Qmat: DenseMatrix[Double] = DenseMatrix.tabulate(sampleSize, sampleSize)((i, j) => QmatArray.find(x => x._1 == i && x._2 == j).map(_._3).getOrElse(0.0))
    println("qMatCollTime time: " + (System.nanoTime - qMatCollTime) / 1000000 + "ms")

    val numMatCollTime = System.nanoTime()
    val nummatArray = num.map { case ((i, j), v) => (i, j, v) }.collect()
    val nummat: DenseMatrix[Double] = DenseMatrix.tabulate(sampleSize, sampleSize)((i, j) => nummatArray.find(x => x._1 == i && x._2 == j).map(_._3).getOrElse(0.0))
    println("numMatCollTime time: " + (System.nanoTime - numMatCollTime) / 1000000 + "ms")

    //val Pmat = DenseMatrix.tabulate(sampleSize, sampleSize) { (i, j) => P.sortByKey().lookup((i, j)).headOption.getOrElse(0.0) }
    //val Qmat = DenseMatrix.tabulate(sampleSize, sampleSize) { (i, j) => Q.sortByKey().lookup((i, j)).headOption.getOrElse(0.0) }
    //val nummat = DenseMatrix.tabulate(sampleSize, sampleSize) { (i, j) => num.sortByKey().lookup((i, j)).headOption.getOrElse(0.0) }


    val PQmat = Pmat - Qmat

    for (iter <- 0 until max_iter) {

      // compute gradient: insert into every row of dCdy 4*sum_j(p_ij - q_ij)(y_i - y_j) * (1 + L2)^-1
      // see equation (5) in the original paper: https://jmlr.org/papers/volume9/vandermaaten08a/vandermaaten08a.pdf
      // y points are points in the low-dim space that are moved into clusters by the optimization.
      for (i <- 0 until n) {
//        println(i)
        val currentMat = tile(PQmat(::, i) *:* nummat(::, i), 1, k) // 10x2
        val secondMat = (stackVector(Ymat(i, ::).t, n) - Ymat) // 10x2
        val rowY = sum(currentMat *:* secondMat, Axis._0)
        dCdY(i, ::) := sum(tile(PQmat(::, i) *:* nummat(::, i), 1, k) *:* (stackVector(Ymat(i, ::).t, n) - Ymat), Axis._0)
      }

      // Perform GD update
      val momentum = if (iter <= 20) initial_momentum else final_momentum
      gains.foreachPair {
        case ((i, j), old_gain) =>
          val new_gain = math.max(min_gain,
            if ((dCdY(i, j) > 0.0) != (iY(i, j) > 0.0))
              old_gain + 0.2
            else
              old_gain * 0.8
          )
          gains.update(i, j, new_gain)

          val new_iY = momentum * iY(i, j) - lr * new_gain * dCdY(i, j)
          iY.update(i, j, new_iY)

          Ymat.update(i, j, Ymat(i, j) + new_iY) // Y += iY

      }
    }
    Ymat
  }

  // Define main function
  def main(args: Array[String]): Unit = {
    // testing tSNEsimple
    val totalTime = System.nanoTime()
    val YmatOptimized = tSNEsimple(X = MNISTdata,
      P = computeSimilarityScoresGauss(pairwiseDistances(MNISTdata), sigma = 1),
      Q = computeSimilarityScoresT(pairwiseDistances(MNISTdata))._1,
      num = computeSimilarityScoresT(pairwiseDistances(MNISTdata))._2,
      max_iter = 5,
    )
    println("Total time: " + (System.nanoTime - totalTime) / 1000000 + "ms")
  }


//  println("Ymat Optimized: ")
//  YmatOptimized

}


