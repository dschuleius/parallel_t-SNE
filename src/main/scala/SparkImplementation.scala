// https://spark.apache.org/docs/latest/rdd-programming-guide.html


import org.apache.spark.SparkContext
import org.apache.spark.SparkConf
import org.apache.spark.mllib._
import org.apache.spark.storage.StorageLevel

import scala.io.Source
import breeze.linalg._
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.linalg.distributed.RowMatrix
import org.apache.spark.rdd.RDD

object SparkImplementation extends App{

  val conf = new SparkConf().setAppName("distributed_t-SNE").setMaster("local[1]").set("spark.driver.host", "127.0.0.1").set("spark.driver.bindAddress", "127.0.0.1")
  val sc = new SparkContext(conf)

  def importData(fileName: String, sampleSize: Int): Array[Array[Double]] = {
    // Read the file and split it into lines
    val lines = Source.fromFile(fileName).getLines.take(sampleSize).toArray

    // Split each line into fields and convert the fields to doubles
    // trim removes leading and trailing blank space from each field
    val data = lines.map(_.trim.split("\\s+").map(_.toDouble))

    // Return the data as an array of arrays of doubles
    data
  }

  // calling sc.parallelize to create 2 RDDs from textfile.
  // relative path does not work, probably problem with SBT folder structure
  val MNISTlabels = sc.parallelize(importData("/Users/juli/Documents/WiSe_2223_UniBo/ScalableCloudProg/parralel_t-SNE/data/mnist2500_labels.txt", 10))
  val MNISTdata = sc.parallelize(importData("/Users/juli/Documents/WiSe_2223_UniBo/ScalableCloudProg/parralel_t-SNE/data/mnist2500_X.txt", 10))
  MNISTdata.take(10).foreach(println)

}
