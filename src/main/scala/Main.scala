import org.apache.spark.{HashPartitioner, RangePartitioner, SparkConf, SparkContext}

import scala.io.Source
import org.apache.spark.mllib.linalg.{Matrix, Vector, Vectors}
import org.apache.spark.mllib.linalg.distributed.{CoordinateMatrix, MatrixEntry, RowMatrix}
import org.apache.spark.rdd.RDD
import breeze.linalg._
import breeze.numerics.{exp, log}
import org.apache.spark.mllib.rdd.MLPairRDDFunctions.fromPairRDD
import org.yaml.snakeyaml.Yaml

//import scala.collection.LazyZipOpsImplicits._ / neceassy for lazy zip in scala 2.12

object Main {

  private val yaml = new Yaml()
  private val ios = Source.fromResource("config.yaml").mkString
  private val obj = yaml.load(ios).asInstanceOf[java.util.Map[String, Any]]


  private def getConfInt(valName: String): Int = {
    val nestedValue = obj.get(valName).asInstanceOf[Int]
    nestedValue
  }

  private def getConfString(valName: String): String = {
    val nestedValue = obj.get(valName).asInstanceOf[String]
    nestedValue
  }

  private def getNestedConfString(functionName: String, valName: String): String = {
    val nestedValue = obj.get(functionName).asInstanceOf[java.util.Map[String, Any]].get(valName).asInstanceOf[String]
    nestedValue
  }

  private def getNestedConfInt(functionName: String, valName: String): Int = {
    val nestedValue = obj.get(functionName).asInstanceOf[java.util.Map[String, Any]].get(valName).asInstanceOf[Int]
    nestedValue
  }

  private def getNestedConfBoolean(functionName: String, valName: String): Boolean = {
    val nestedValue = obj.get(functionName).asInstanceOf[java.util.Map[String, Any]].get(valName).asInstanceOf[Boolean]
    nestedValue
  }

  private def getNestedConfDouble(functionName: String, valName: String): Double = {
    val nestedValue = obj.get(functionName).asInstanceOf[java.util.Map[String, Any]].get(valName).asInstanceOf[Double]
    nestedValue
  }



  // set up Spark, changing to local host.
  val conf: SparkConf = new SparkConf()
    .setAppName(getNestedConfString("sparkConfig", "appName"))
    .setMaster(getNestedConfString("sparkConfig","master"))
    .set("spark.driver.host", getNestedConfString("sparkConfig","sparkBindHost"))
    .set("spark.driver.bindAddress", getNestedConfString("sparkConfig","sparkBindAddress"))
    //.set("spark.sql.shuffle.partitions", "10")
  val sc = new SparkContext(conf)
  sc.setLogLevel("ERROR")   // show only Error and not Info messages


  // function that imports MNIST from .txt files.
  def importData(fileName: String, sampleSize: Int): Array[(Int, Array[Double])] = {
    // read the file and split it into lines
//    val lines = Source.fromFile("gs://scala-and-spark/resources/mnist2500_X.txt").getLines.take(sampleSize).toArray
    val lines = sc.textFile("gs://scala-and-spark/resources/mnist2500_X.txt").take(sampleSize).toArray

    // split each line into fields and convert the fields to doubles
    // trim removes leading and trailing blank space from each field
    val data = lines
      .zipWithIndex
      .map{ case (arr, i) => (i, arr.trim.split("\\s+").map(_.toDouble)) }

    // return the data as an tuple of the index and an array of arrays of doubles
    data

  }



  def euclideanDistance(point1: Array[Double], point2: Array[Double]): Double = {
    // Calculate the squared Euclidean distance between the two points
    val squaredDistance = point1.zip(point2).map { case (x, y) => math.pow(x - y, 2) }.sum

    // Return the Euclidean distance
    math.sqrt(squaredDistance)
  }


  def computeYdiff(points: RDD[((Int, Int), Double)]): RDD[((Int, Int), Array[Double])] = {
    val zippedPoints = points
      .sortByKey()
      .map{ case ((i, j), d) => (i, d)}
      .groupByKey()
      .map{ case (i, iterable) => (i, iterable.toArray)}
    val differences = zippedPoints.cartesian(zippedPoints)
      .flatMap {
        case ((i, u), (j, v)) =>
          val dist = u.zip(v).map { case (a, b) => a - b }
          Seq(((i, j), dist))
      }
    differences
  }


  case class VectorAndNorm(vector: DenseVector[Double], norm: Double)

  def entropyBeta(D: DenseVector[Double], beta: Double = 1.0): (Double, DenseVector[Double]) = {
    val P: DenseVector[Double] = exp(-D * beta) // beta = 1 / 2 * sigma_i
    val sumP = sum(P)
    if (sumP == 0) {
      (0.0, DenseVector.zeros(D.size))
    } else {
      val H = log(sumP) + (beta * sum(D *:* P) / sumP)
      (H, P / sumP)
    }
  }


  def computeP(X: RDD[Array[Double]],
               tol: Double = 1e-5,
               perplexity: Double = 30.0,
               n: Int,
               partitions: Int,
               kNNapprox: Boolean): RDD[((Int, Int), Double)] = {
    assert(tol >= 0, "Tolerance must be non-negative")
    assert(perplexity > 0, "Perplexity must be positive")

    val logU = Math.log(perplexity)
    val norms = X.map{ arr => Vectors.norm(Vectors.dense(arr), 2.0) }
    val rowsWithNorm = X.zip(norms).map { case (v, norm) => VectorAndNorm(DenseVector(v), norm) }
    val distancesUngrouped = rowsWithNorm.zipWithIndex()
      .cartesian(rowsWithNorm.zipWithIndex())
      .flatMap {
        case ((u, i), (v, j)) =>
          if (i < j) {
            val dist = math.pow(euclideanDistance(u.vector.toArray, v.vector.toArray), 2)
            Seq((i, (j, dist)), (j, (i, dist)))
          } else {
            Seq.empty
          }
      }
    val distances: RDD[(Long, Iterable[(Long, Double)])] = {
      if (kNNapprox) {
        distancesUngrouped.groupByKey() //substitute .topByKey(3 * perplexity)(Ordering.by(entry => -entry._2)) for kNN approximation
      } else {
        distancesUngrouped
          .topByKey((3 * perplexity).toInt)(Ordering.by(entry => -entry._2))
          .map{ case (i, arr) => (i, arr.toIterable)}
      }
    }

    val p_sigma =
      distances.map {
        case (i, arr) =>
          var betamin = Double.NegativeInfinity
          var betamax = Double.PositiveInfinity
          var beta = 1.0

          val d = DenseVector(arr.map(_._2).toArray)
          var (h, p) = entropyBeta(d, beta)

          // evaluate if perplexity is within tolerance
          var Hdiff: Double = h - logU
          var numtries = 0

          while (Math.abs(Hdiff) > tol && numtries < 50) {

            // if not, increase or decrease precision
            if (Hdiff > 0) {
              betamin = beta
              beta = if (betamax.isInfinite) beta * 2 else (beta + betamax) / 2
            } else {
              betamax = beta
              beta = if (betamin.isInfinite) beta / 2 else (beta + betamin) / 2
            }

            // recompute the values
            val hp = entropyBeta(d, beta)
            h = hp._1
            p = hp._2
            Hdiff = h - logU
            numtries = numtries + 1
          }

          // map over the arr Array, combine the row indices with the values from p, and create a new Array of MatrixEntry objects.
          arr.map(_._1).zip(p.toArray).map { case (j, v) => MatrixEntry(i, j, v) }
      }

    // normalize entries of P
    val Punnorm = new CoordinateMatrix(p_sigma.flatMap(me => me))
      .entries.map{ case MatrixEntry(i, j, v) => ((i.toInt, j.toInt), v) }
    val PunnormZeros = Punnorm
      .map{ case ((i, j), value) => ((i, i), 0.0)}
      .union(Punnorm)
      .distinct()
      .partitionBy(new RangePartitioner(partitions = partitions, Punnorm))

    val P_t = PunnormZeros.map { case ((i, j), v) => ((j, i), v) }
    val PP_t = PunnormZeros.union(P_t)
      .reduceByKey((v1, v2) => v1 + v2)
    val PP_tsum = PP_t.map(_._2).reduce(_ + _)
    val P = PP_t.map { case ((i, j), d) => ((i, j), 4 * (d / PP_tsum)) } //early exaggeration
    P.map { case ((i, j), value) =>
      if (i == j) ((i, j), 1e-12) else ((i, j), value)
    }

    P
  }



  // PCA function that takes the original input data and applies PCA as initialization for t-SNE
  def PCA(data: RDD[(Int, Array[Double])], reduceTo: Int, sampleSize: Int): RDD[(Int, Array[Double])] = {
    val rows = data
      .sortByKey()
      .map{ case (index, arr) => (index, Vectors.dense(arr)) }

    val meanVec  = Vectors.dense(rows.map(_._2.toArray).reduce ((a, b) => (a,b).zipped.map(_ + _)).map(_ / sampleSize))

    // Subtract the means from each vector in the RDD
    val normalizedVecs: RDD[(Int, Vector)] = rows
        .map { case( index, vec) => (index, Vectors.dense(vec.toArray.zip(meanVec.toArray)
        .map { case (a, b) => a - b }))
    }

    val arrayData = normalizedVecs
      .collect()
      .sortWith((a, b) => a._1 < b._1)
      .map(_._2.toArray)

    val dmmat = DenseMatrix(arrayData:_*)
    val mat = new RowMatrix(normalizedVecs.map(_._2))

    // compute the top k principal components.
    val pc: Matrix = mat.computePrincipalComponents(reduceTo)

    val dmpc: DenseMatrix[Double] = DenseMatrix.create(pc.numRows, pc.numCols, pc.toArray)

    // project the rows to the linear space spanned by the top reduceTo principal components.
    val projected = dmmat * dmpc

    val projectedArr = (for (i <- 0 until projected.rows) yield (i, projected(i, ::).inner.toArray)).toArray

    sc.parallelize(projectedArr)
      .sortByKey()
  }



  def tSNE(data: RDD[(Int, Array[Double])], // dims already reduced using PCA
                 k: Int = 2, // number of target dims after t-SNE has been applied to the data
                 max_iter: Int = 100,
                 initial_momentum: Double = 0.5,
                 final_momentum: Double = 0.8,
                 partitions: Int = 2, // set to number of CPU cores available
                 lr: Double = 500,
                 minimumgain: Double = 0.01,
                 sampleSize: Int,
                 export: Boolean = false,
                 printing: Boolean = false,
                 takeSamples: Int,
                 kNNapprox: Boolean):
  RDD[((Int, Int), Double)] = {

    // initialize variables
    val initVarTime = System.nanoTime()
    val momRDDunpar: RDD[((Int, Int), Double)] = sc.parallelize(0 until sampleSize * k)
      .map{ i =>
        val row = i / k
        val col = i % k
        ((row, col), 0.0)
      }
      .sortByKey()
    var momRDD = momRDDunpar.partitionBy(new RangePartitioner(partitions = partitions, momRDDunpar))

    data.sortByKey()
    data.partitionBy(new RangePartitioner(partitions = partitions, momRDDunpar))

    // testing with non-random Y
    /*
    val Y = Array(Array(0.1, -0.2), Array(1.2, 0.8), Array(-0.2, 0.6), Array(-0.9, 0.1), Array(1.3, 0.5))
    val Y = Array(
      Array(-1.12870294, -0.83008814),
      Array(0.07448544, -1.30711223),
      Array(1.02539386, -0.32798679),
      Array(0.60880325, -0.59266964),
      Array(0.34561165, -0.83674335),
      Array(-0.90895435, -0.02894896),
      Array(-0.73702659, 1.22795944),
      Array(0.30497069, -0.33730157),
      Array(0.01755616, -0.58677067),
      Array(0.73303266, -0.70495823),
      Array(-0.57352783, 0.95559499),
      Array(-0.95133737, 0.41451273),
      Array(0.35576788, -0.59659931),
      Array(-0.88165797, 0.70285842),
      Array(1.46847176, -0.31269423),
      Array(-0.02516202, 0.84827782),
      Array(1.54981666, 0.14326029),
      Array(0.6373717, 0.45151671),
      Array(-0.12505717, 1.02270182),
      Array(-0.01494444, 0.78451147),
      Array(-0.90826369, 0.87060796),
      Array(-0.26584847, -0.16205853),
      Array(-0.6810938, 0.54225839),
      Array(-1.00846689, 0.04482262),
      Array(-0.09231624, 1.6466729),
      Array(0.31236914, -0.33153579),
      Array(0.59300798, 0.76617706),
      Array(-0.55785569, -0.41471199),
      Array(-0.46165087, -0.80576489),
      Array(1.05787231, 0.31475158),
      Array(-0.46556687, 1.48494896),
      Array(-1.63585795, 0.64046439),
      Array(0.73408097, -0.59385824),
      Array(1.26926492, 0.85999462),
      Array(0.05963874, -0.82447235),
      Array(0.08331667, 1.19259051),
      Array(0.89707891, -0.47827121),
      Array(0.38673393, -0.63408034),
      Array(0.12074724, 1.77694826),
      Array(-0.30570598, 0.53870262))

    var YRDD = sc.parallelize(Y).zipWithIndex().flatMap {
      case (values, outerIndex) => values.zipWithIndex.map {
        case (value, innerIndex) => ((outerIndex.toInt, innerIndex), value)
      }
    }
   */

    var YRDD = sc.parallelize(0 until sampleSize * k)
      .map { i =>
        val row = i / k
        val col = i % k
        ((row, col), randn())
      }.sortByKey()
      .partitionBy(new RangePartitioner(partitions = partitions, momRDD))

    // gains matrix that stores adaptive learning rates
    var gains = DenseMatrix.ones[Double](sampleSize, k)

    if (printing) {
      println("First n rows of YRDD after initialization: ")
      YRDD.take(takeSamples).foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))
    }

    val PRDD = computeP(data.map(_._2), n = sampleSize, partitions = partitions, kNNapprox = kNNapprox).partitionBy(new RangePartitioner(partitions = partitions, momRDD))
    PRDD.map { case ((i, j), p) => ((i, j), math.max(p, 1e-12))}

    if (printing) {
      println("Initialization done, YRDD has dimensions: " + YRDD.map(_._1._1).reduce(math.max).toString + " x " + YRDD.map(_._1._2).reduce(math.max).toString)
      println("Is PRDD empty? " + PRDD.isEmpty().toString + ". It has dimension: " + PRDD.map(_._1._1).reduce(math.max).toString + " x " + PRDD.map(_._1._2).reduce(math.max).toString)
      println("This is PRDD: ___________________")
      PRDD.take(takeSamples).foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))
    }


    var iter: Int = 0

    while (iter < max_iter) {

      // compute gradient: insert into every row of dCdy_i = 4 * \sum_j (p_{ij} - q_{ij})(y_i - y_j)(1 + ||y_i - y_j||^2)^{-1}
      // see equation (5) in the original paper: https://jmlr.org/papers/volume9/vandermaaten08a/vandermaaten08a.pdf
      // y points are points in the low-dim space that are moved into clusters by the optimization.

      println("_____________________________________________")
      println("_____________________________________________")
      println("_____________________________________________")
      println("Starting iteration number " + iter.toString)
      if (printing) {
        println("YRDD looks like this at the start of iteration number " + iter.toString)
        YRDD.take(takeSamples).foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))
      }

      // calculate QRDD and num
      // distance matrix
      val zippedPoints = YRDD
        .sortByKey()
        .map { case ((i, j), d) => (i, d) }
        .groupByKey() // changes the order deliberately, that is why we need to sortByKey() first!!
        .map { case (i, itable) => (i, itable.toArray) }
      val distances = zippedPoints.cartesian(zippedPoints)
        .flatMap {
          case ((i, u), (j, v)) =>
            if (i < j) {
              val dist = euclideanDistance(u, v)
              Seq(((i, j), dist), ((j, i), dist))
            } else if (i == j) {
              Seq(((i, j), 0.0))
            } else {
              Seq.empty
            }
        }

      if (printing) {
        println("____________________________________")
        println(" These are the distances for Q and num in iteration " + iter.toString)
        distances.take(takeSamples).foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))
      }

      // computation of num matrix and Q matrix, both RDDs
      val num = distances.map { case ((i, j), d) =>
        if (i != j) {
          ((i, j), 1.0 / (1.0 + scala.math.pow(d, 2)))
        } else {
          ((i, j), d)
        }
      }.partitionBy(new RangePartitioner(partitions = partitions, momRDD))
      num.cache()

      val denom = num.map(_._2).reduce(_ + _)

      val QRDD = num
        .map { case ((i, j), d) => ((i, j), d / denom) }
        .partitionBy(new RangePartitioner(partitions = partitions, momRDD))
      QRDD.cache()


      QRDD.map { case ((i, j), q) => ((i, j), math.max(q, 1e-12)) }

      if (printing) {
        //println(" QRDD looks like this after iteration " + iter.toString + "_________")
        //QRDD.foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))
        //println("_________________________________________________")
        //println("num looks like this after iteration " + iter.toString + "_________")
        //num.foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))
      }

      val PQRDD = PRDD.join(QRDD).map { case ((i, j), (p, q)) => ((i, j), p - q) }
        .partitionBy(new RangePartitioner(partitions = partitions, momRDD))
      PQRDD.cache()
      //println("_________________________________________________")
      //println("Is PQRDD empty in iteration number " + iter.toString + "?" + PQRDD.isEmpty().toString + ". It has dimension: " + PQRDD.map(_._1._1).reduce(math.max).toString + " x " + PQRDD.map(_._1._2).reduce(math.max).toString )
      //println(" PQRDD looks like this after iteration " + iter.toString + "_________")
      //PQRDD.foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))


      val ydiff = computeYdiff(YRDD)
        .partitionBy(new RangePartitioner(partitions = partitions, momRDD))
      ydiff.cache()
      //println("Is ydiff empty? " + ydiff.isEmpty().toString + ". It has dimension: " + ydiff.map(_._1._1).reduce(math.max).toString + " x " + ydiff.map(_._1._2).reduce(math.max).toString )
      //println(" ydiff looks like this after iteration " + iter.toString)
      //ydiff.take(takeSamples).foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2.mkString("Array(", ", ", ")")}"))


      val dCdYRDD = PQRDD.join(num).join(ydiff).map{ case ((i, j), ((pq, num), diff)) => ((i, j), diff.map(_ * pq * num))}
        .sortByKey()
        .map{ case ((i, j), comp) => (i, comp) }
        .groupByKey()
        .mapValues(arrays => arrays.reduce((a, b) => a.zip(b).map{ case (x, y) => x + y }))
        .flatMap{ case (i, values) => values.zipWithIndex.map{ case (value, j) => ((i, j), value) } }
        .partitionBy(new RangePartitioner(partitions = partitions, momRDD))
      dCdYRDD.cache()

      //println("_________________________________________________")
      //println("Is dCdYRDD empty? " + dCdYRDD.isEmpty().toString + ". It has dimension: " + dCdYRDD.map(_._1._1).reduce(math.max).toString + " x " + dCdYRDD.map(_._1._2).reduce(math.max).toString)
      //println("dCdYRDD looks like this after iteration " + iter.toString)
      //dCdYRDD.take(takeSamples).foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))


      // Gradient Descent step with momentum
      val momentum = if (iter < 20) initial_momentum else final_momentum

      gains.foreachPair {
        case ((i, j), old_gain) =>
          val new_gain = math.max(minimumgain,
            if ((dCdYRDD.filter { case ((key1, key2), value) =>
              (key1, key2) == (i, j)
            }.map(_._2).first() > 0.0) != (momRDD.filter { case ((key1, key2), value) =>
              (key1, key2) == (i, j)
            }.map(_._2).first() > 0.0))
              old_gain + 0.2
            else
              old_gain * 0.8
          )
          gains.update(i, j, new_gain)
      }

      val gainsArray = gains.toArray        // Python: gains[gains < min_gain] = min_gain, set all gains that are smaller
      for (i <- gainsArray.indices) {       // than min_gain to min_gain
        if (gainsArray(i) < minimumgain) {
          gainsArray(i) = minimumgain
        }
      }
      gains = DenseMatrix(gainsArray).reshape(gains.rows, gains.cols)

      if (printing) {
        println("_________________________________________________")
        println("gains in iteration " + iter.toString)
        println(gains)

        println("_________________________________________________")
        println("momRDD in iteration " + iter.toString + " BEFORE UPDATE WITH CURRENTDERIV")
        momRDD.take(takeSamples).foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))
      }




      momRDD = momRDD.join(dCdYRDD).map{ case ((i, j), (oldderiv, currentderiv)) =>
        ((i, j), (momentum * oldderiv) - lr * (gains(i, j) * currentderiv)) }
        .sortByKey()
        .partitionBy(new RangePartitioner(partitions = partitions, momRDD))

      if (printing) {
        println("_________________________________________________")
        println("momRDD after iteration " + iter.toString)
        momRDD.foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))
      }

      // apply update
      YRDD = YRDD.join(momRDD).map { case ((i, j), (current, update)) => ((i, j), current + update) }


      // subtract the mean of each column from each corresponding element in the column
      val YRDDColSumsMap = YRDD
        .map{ case ((row, col), value) => (col, value) }
        .reduceByKey(_ + _)
        .collect()
        .toMap
      YRDD = YRDD.map{case ((i, j), y) => ((i, j), y - (YRDDColSumsMap(j) / sampleSize.toDouble))}

      println("Finishing iteration number " + iter.toString + ".")
      println("YRDD has the following dimensions after iteration number " + iter.toString + ": " + YRDD.map(_._1._1).reduce(math.max).toString + " x " + YRDD.map(_._1._2).reduce(math.max).toString)
      println("Is YRDD empty after iteration number " + iter.toString + "? " + YRDD.isEmpty().toString)

      if (printing) {
        YRDD.take(takeSamples).foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))
      }

      iter = iter + 1

      // stop early exaggeration from iter 100 onwards
      if (iter == 100) {
        PRDD.map{ case ((i, j), p) => ((i, j), p / 4.0) }
      }

      //simscoresYRDD.unpersist()
      QRDD.unpersist(blocking = true)
      num.unpersist(blocking = true)
      PQRDD.unpersist(blocking = true)
      ydiff.unpersist(blocking = true)
      dCdYRDD.unpersist(blocking = true)

      // visualization
      if (export) {
        val fs = org.apache.hadoop.fs.FileSystem.get(sc.hadoopConfiguration)
        val path = new org.apache.hadoop.fs.Path("data/export/")
        if (fs.exists(path)) {
          fs.delete(path, true)
        }
        val exportYRDD = YRDD.coalesce(1)
          .sortByKey()
          .map{ case ((i, j), d) => (i, d)}
          .sortByKey()  // probably not needed
          .groupByKey()
          .sortByKey()
          .map{ case (row, values) => (row, values.mkString(", ")) }
        exportYRDD.map{ case (i, str) => str }.saveAsTextFile("data/export/exportIter_" + iter.toString)
      }
    }
    YRDD
  }


  // Define main function
  def main(args: Array[String]): Unit = {
    val sampleSize: Int = getNestedConfInt("main", "sampleSize") // SET THIS CORRECTLY
    val partitions: Int = getNestedConfInt("main", "partitions")

    val toRDDTime = System.nanoTime()

    val MNISTdata = sc.parallelize(
      importData(getConfString("dataFile"),
      getNestedConfInt("main", "sampleSize")))


    println("To RDD time for " + sampleSize + " samples: " + (System.nanoTime - toRDDTime) / 1000000 + "ms")


    val MNIST_PCA = PCA(
      data = MNISTdata,
      sampleSize = getNestedConfInt("main", "sampleSize"),
      reduceTo = getNestedConfInt("PCA", "reduceTo"),
    )

    // testing tSNEsimple
    val totalTime = System.nanoTime()

    val YmatOptimized = tSNE(
      data = MNIST_PCA,
      k = getNestedConfInt("tSNE", "k"),
      max_iter = getNestedConfInt("tSNE", "max_iter"),
      initial_momentum = getNestedConfDouble("tSNE", "initial_momentum"),
      final_momentum = getNestedConfDouble("tSNE", "final_momentum"),
      partitions = getNestedConfInt("tSNE", "partitions"),
      export = getNestedConfBoolean("tSNE", "export"),
      lr = getNestedConfDouble("tSNE", "lr"),
      minimumgain = getNestedConfDouble("tSNE", "minimumgain"),
      sampleSize = getNestedConfInt("main", "sampleSize"),
      printing = getNestedConfBoolean("tSNE", "print"),
      takeSamples = getNestedConfInt("tSNE", "takeSamples"),
      kNNapprox = getNestedConfBoolean("tSNE", "kNNapprox"))

    println("_______________________________________")
    println("ENDRESULT YRDD:")
    YmatOptimized.foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))

    println("Total time: " + (System.nanoTime - totalTime) / 1000000 + "ms")

  }

}

