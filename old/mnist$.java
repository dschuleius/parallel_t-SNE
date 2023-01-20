object mnist extends App{

        val spark:SparkSession=SparkSession.builder()
        .master("local[3]")
        .appName("SparkByExample")
        .getOrCreate()

        // Read MNIST dataset
        val mnist=spark.read.format("csv")
        .option("header","false")
        .option("inferSchema","false")
        .load("path/to/mnist_dataset.csv")


        val mnistRDD:RDD[(Double,Vector)]=mnist.rdd.map{row=>
        val label=row.getDouble(0)
        val features=Vectors.dense(row.getString(1).split(",").map(_.toDouble))
        (label,features)
        }


//  val conf = new SparkConf().setAppName("MNIST").setMaster("local")
//  val sc = new SparkContext(conf)
//  val mnist = spark.parallelize(mnistNumpyArray.map(a => (a.label, a.features))
//  val mnistRDD = mnist.map{ case (label, features) =>
//    val featuresVec = new DenseVector(features.flatten)
//    (label, featuresVec)
//  }


//
//  // Convert to RDD
//  val mnistRDD: RDD[(Double, Vector)] = mnist.rdd.map { row =>
//    val label = row.getDouble(0)
//    val features = Vectors.dense(row.getString(1).split(",").map(_.toDouble))
//    (label, features)
//  }

//  package com.sparkbyexamples.spark.rdd
//
//  import org.apache.spark.rdd.RDD
//  import org.apache.spark.sql.SparkSession
//
//  object RDDFromCSVFile {
//
//    def main(args: Array[String]): Unit = {
//
//      def splitString(row: String): Array[String] = {
//        row.split(",")
//      }
//
//      val spark: SparkSession = SparkSession.builder()
//        .master("local[3]")
//        .appName("SparkByExample")
//        .getOrCreate()
//      val sc = spark.sparkContext
//
//      val rdd = sc.textFile("src/main/resources/zipcodes-noheader.csv")
//
//      val rdd2: RDD[ZipCode] = rdd.map(row => {
//        val strArray = splitString(row)
//        ZipCode(strArray(0).toInt, strArray(1), strArray(3), strArray(4))
//      })
//
//      rdd2.foreach(a => println(a.city))
//    }
//
//  }

        }
