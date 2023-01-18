// https://spark.apache.org/docs/latest/rdd-programming-guide.html


import org.apache.spark.SparkContext
import org.apache.spark.SparkConf
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

  // testing initialPCA
  initialPCA(MNISTdata).foreach(arr => println(arr.mkString(",")))



}


