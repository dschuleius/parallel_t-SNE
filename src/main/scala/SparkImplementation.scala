// https://spark.apache.org/docs/latest/rdd-programming-guide.html


import org.apache.spark.SparkContext
import org.apache.spark.SparkConf
import org.apache.spark.mllib._
import org.apache.spark.storage.StorageLevel

import scala.io.Source
import breeze.linalg._
import breeze.storage._
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.linalg.distributed.RowMatrix
import org.apache.spark.rdd.RDD

object SparkImplementation extends App{

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

  def computeSimilarityScoresGauss(distances: RDD[((Int, Int), Double)], sigma: Double): RDD[((Int, Int), Double)] = {

    val n = distances.count().toInt
    val unnormSimilarities = distances.map { case ((i, j), d) =>
      ((i, j), math.exp(-1 * scala.math.pow(d, 2) / (2 * scala.math.pow(sigma, 2))))
    }

    val denominators = unnormSimilarities.map { case ((i, _), _) => i }.distinct().map { i =>
      (i, unnormSimilarities.filter { case ((k, _), _) => k != i }.map { case (_, d) =>
        math.exp(-1 * scala.math.pow(d, 2) / (2 * scala.math.pow(sigma, 2)))
      }.sum())}.map { case (ind, sim) => ((ind, 0), sim)}

    val unnormSimilaritiesWithDenominator = unnormSimilarities.join(denominators).map { case ((i, j), (unnorm, denominator)) =>
      ((i, j), unnorm / denominator) }

    val normSimilarities = unnormSimilaritiesWithDenominator.map { case ((i, j), s) =>
      ((i, j), (s + unnormSimilaritiesWithDenominator.lookup((j, i)).head) / (2 * n))
    }
    normSimilarities
  }


  // testing computeSimilarityScoreGauss
  computeSimilarityScoresGauss(pairwiseDistances(MNISTdata), sigma = 1).take(10).foreach(println)
}
