// https://spark.apache.org/docs/latest/rdd-programming-guide.html


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

object SparkImplementation extends App {

  // set up Spark, changing to local host.
  val conf = new SparkConf()
    .setAppName("distributed_t-SNE")
    .setMaster("local[1]")
    .set("spark.driver.host", "127.0.0.1")
    .set("spark.driver.bindAddress", "127.0.0.1")
  val sc = new SparkContext(conf)

  // function that imports MNIST from .txt files.
  def importData(fileName: String, sampleSize: Int): Array[Array[Double]] = {
    // read the file and split it into lines
    val lines = Source.fromFile(fileName).getLines.take(sampleSize).toArray

    // split each line into fields and convert the fields to doubles
    // trim removes leading and trailing blank space from each field
    val data = lines.map(_.trim.split("\\s+").map(_.toDouble))

    // return the data as an array of arrays of doubles
    data
  }

  // calling sc.parallelize to create 2 RDDs from textfile.
  // relative path does not work, probably problem with SBT folder structure
  val MNISTlabels = sc.parallelize(importData("/Users/juli/Documents/WiSe_2223_UniBo/ScalableCloudProg/parralel_t-SNE/data/mnist2500_labels.txt", 10))
  val MNISTdata = sc.parallelize(importData("/Users/juli/Documents/WiSe_2223_UniBo/ScalableCloudProg/parralel_t-SNE/data/mnist2500_X.txt", 10))

  // testing
  MNISTdata.take(10).foreach(println)

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

  pairwiseDistances(MNISTdata).take(5).foreach(println)

  // !! still to do: perplexity calculation instead of constant sigma !!
  def computeSimilarityScoresGauss(distances: RDD[((Int, Int), Double)], sigma: Double): RDD[((Int, Int), Double)] = {

    val n = distances.count().toInt
    val unnormSimilarities = distances.map { case ((i, j), d) =>
      ((i, j), math.exp(-1 * scala.math.pow(d, 2) / (2 * scala.math.pow(sigma, 2))))
    }

    // problem: RDD transformation invoked inside of other transformation: not allowed!!
    //val denominators = unnormSimilarities.map { case ((i, _), _) => i }.distinct() //.map { i => (i,

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
  computeSimilarityScoresGauss(pairwiseDistances(MNISTdata), sigma = 1).take(10).foreach(println)

  // returns a tuple of 2 RDDs, "num" containing the numerator values, which are later needed for the gradient
  // computation, "normSimilarities" containing the Similarity Scores in the low-dim. representation.
  def computeSimilarityScoresT(distances: RDD[((Int, Int), Double)]): (RDD[((Int, Int), Double)], RDD[((Int, Int), Double)]) = {

    val n = distances.count().toInt
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
  computeSimilarityScoresT(pairwiseDistances(MNISTdata))._1.take(10).foreach(println)


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
  println("Testing builtin PCA function:")
  mlPCA(MNISTdata).foreach(arr => println(arr.mkString(",")))


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
                 lr: Double = 500):
  DenseMatrix[Double] = {

    assert(P.count() == Q.count(), "SimilarityScore multi-dim. Arrays must have the same number of rows.")
    assert(P.first().length == Q.first().length, "SimilarityScore multi-dim. Arrays must have the same number of columns.")
    assert(P.count() == P.first().length && Q.count() == Q(0).length, "SimilarityScore multi-dim. Arrays must be symmetric.")

    // initialize variables
    val n: Int = P.count().toInt
    val dCdY = DenseMatrix.zeros[Double](n, k)
    val iY = DenseMatrix.zeros[Double](n, k)
    val gains = DenseMatrix.ones[Double](n, k)
    val Ymat = DenseMatrix.zeros[Double](n, k)

    val Pmat = new DenseMatrix[Double](P.count().toInt, P.first().length, P.collect().flatten)
    val Qmat = new DenseMatrix[Double](Q.count().toInt, Q.first().length, Q.collect().flatten)
    val nummat = new DenseMatrix[Double](num.count().toInt, num.first().length, num.collect().flatten)

    val PQmat = Pmat - Qmat

    for (iter <- 0 until max_iter) {

      // compute SimilarityScores in low dim:
      val Ymat: new DenseMatrix[Double](k, mlPCA(X).first().length, mlPCA(X).collect().flatten)

      // compute gradient: insert into every row of dCdy 4*sum_j(p_ij - q_ij)(y_i - y_j) * (1 + L2)^-1
      // see equation (5) in the original paper: https://jmlr.org/papers/volume9/vandermaaten08a/vandermaaten08a.pdf
      // y points are points in the low-dim space that are moved into clusters by the optimization.
      for (i <- 0 until n) {
        dCdY(i, ::) := sum(tile(PQmat(::, i) *:* nummat(::, i), 1, k).t *:* (stackVector(Ymat(i, ::), n) - Ymat), Axis._0)
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



}


