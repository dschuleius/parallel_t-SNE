// https://spark.apache.org/docs/latest/rdd-programming-guide.html


import org.apache.spark.SparkContext
import org.apache.spark.SparkConf
import org.apache.spark.mllib._

import scala.io.Source
import breeze.linalg._
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.linalg.distributed.RowMatrix
import org.apache.spark.rdd.RDD

object SparkImplementation extends App{
  /*
  val spark = SparkSession.builder()
    .master("local[1]")
    .appName("parallel_t-SNE")
    .config("spark.driver.host", "127.0.0.1")
    .config("spark.driver.bindAddress", "127.0.0.1")
*/

  val conf = new SparkConf().setAppName("distributed_t-SNE").setMaster("local[1]").set("spark.driver.host", "127.0.0.1").set("spark.driver.bindAddress", "127.0.0.1")
  val sc = new SparkContext(conf)


  val dataset = sc.textFile("data/mnist.csv.gz")
    .zipWithIndex()
    .filter(_._2 < 6000)
    .sortBy(_._2, true, 60)
    .map(_._1)
    .map(_.split(","))
    .map(x => (x.head.toInt, x.tail.map(_.toDouble)))
    .cache()

  val data = dataset.flatMap(_._2)
  val mean = data.mean()
  val std = data.stdev()
  val scaledData = dataset.map(x => Vectors.dense(x._2.map(v => (v - mean) / std))).cache()

  val labels = dataset.map(_._1).collect()
  val matrix = new RowMatrix(scaledData)
  val pcaMatrix = matrix.multiply(matrix.computePrincipalComponents(50))
  pcaMatrix.rows.cache()

}
