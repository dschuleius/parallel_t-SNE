import org.apache.spark.{RangePartitioner, SparkConf, SparkContext}

import scala.io.Source
import org.apache.spark.mllib.linalg.{Matrix, Vector, Vectors}
import org.apache.spark.mllib.linalg.distributed.{CoordinateMatrix, MatrixEntry, RowMatrix}
import org.apache.spark.rdd.RDD
import breeze.linalg._
import breeze.numerics.{exp, log}
import org.apache.spark.mllib.rdd.MLPairRDDFunctions.fromPairRDD
import org.yaml.snakeyaml.Yaml


object Main extends Serializable {

  // Define main function
  def main(args: Array[String]): Unit = {

    // YAML configuration
    val yaml = new Yaml()
    val ios = Source.fromResource("config.yaml").mkString
    val obj = yaml.load(ios).asInstanceOf[java.util.Map[String, Any]]


    def getConfString(valName: String): String = {
      val nestedValue = obj.get(valName).asInstanceOf[String]
      nestedValue
    }

    def getNestedConfString(functionName: String, valName: String): String = {
      val nestedValue = obj.get(functionName).asInstanceOf[java.util.Map[String, Any]].get(valName).asInstanceOf[String]
      nestedValue
    }

    def getNestedConfInt(functionName: String, valName: String): Int = {
      val nestedValue = obj.get(functionName).asInstanceOf[java.util.Map[String, Any]].get(valName).asInstanceOf[Int]
      nestedValue
    }

    def getNestedConfBoolean(functionName: String, valName: String): Boolean = {
      val nestedValue = obj.get(functionName).asInstanceOf[java.util.Map[String, Any]].get(valName).asInstanceOf[Boolean]
      nestedValue
    }

    def getNestedConfDouble(functionName: String, valName: String): Double = {
      val nestedValue = obj.get(functionName).asInstanceOf[java.util.Map[String, Any]].get(valName).asInstanceOf[Double]
      nestedValue
    }

    // Spark setup
    // set shuffle.partitions, parallelism and partitions to 3 times number vCPU
    val conf: SparkConf = new SparkConf()
      .setAppName(getNestedConfString("sparkConfig", "appName"))
      .set("spark.sql.shuffle.partitions", getNestedConfString("sparkConfig", "shufflePartitions"))
      .set("spark.default.parallelism", getNestedConfString("sparkConfig", "defaultParallelism"))
      // for local mode:
      if (getNestedConfBoolean("sparkConfig", "local")) {conf
        .setMaster(getNestedConfString("sparkConfig", "master"))
        .set("spark.driver.host", getNestedConfString("sparkConfig", "sparkBindHost"))
        .set("spark.driver.bindAddress", getNestedConfString("sparkConfig", "sparkBindAddress"))
      }
    val sc = new SparkContext(conf)
    sc.setLogLevel("ERROR") // show only Error and not Info messages

    // create sampleRDD for RangePartitioner creation
    val sampleRDD: RDD[((Int, Int), Double)] = sc.parallelize(0 until getNestedConfInt("main", "sampleSize") * getNestedConfInt("tSNE", "k"))
        .map { i =>
          val row = i / getNestedConfInt("tSNE", "k")
          val col = i % getNestedConfInt("tSNE", "k")
          ((row, col), 0.0)
        }
        .sortByKey()

    // create RangePartitioner
    val rp = new RangePartitioner(partitions = getNestedConfInt("main", "partitions"), sampleRDD)

    // function that imports MNIST from .txt files.
    def importData(fileName: String, sampleSize: Int): Array[(Int, Array[Double])] = {
      // read the file and split it into lines
      val lines = sc.textFile(fileName).take(sampleSize)

      // split each line into fields and convert the fields to doubles
      // trim removes leading and trailing blank space from each field
      val data = lines
        .zipWithIndex
        .map { case (arr, i) => (i, arr.trim.split("\\s+").map(_.toDouble)) }

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
        .map { case ((a, b), d) => (a, (b, d)) }
        .groupByKey()
        .mapValues(_.toArray.sortBy(_._1).map(_._2))
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

  // function that computes the entries of the P matrix, p_{ij}, finding appropriate sigma_{i}
  def computeP(X: RDD[Array[Double]],
               tol: Double = 1e-5,
               perplexity: Double = 30.0,
               n: Int,
               kNNapprox: Boolean): RDD[((Int, Int), Double)] = {
    assert(tol >= 0, "Tolerance must be non-negative")
    assert(perplexity > 0, "Perplexity must be positive")

      val logU = Math.log(perplexity)
      val norms = X
        .map { arr => Vectors.norm(Vectors.dense(arr), 2.0) }
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
          distancesUngrouped
            .topByKey((3 * perplexity).toInt)(Ordering.by(entry => -entry._2))
            .map { case (i, arr) => (i, arr.toIterable) } // kNN approximation
        } else {
          distancesUngrouped
            .sortByKey()
            .groupByKey()
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
        .entries.map { case MatrixEntry(i, j, v) => ((i.toInt, j.toInt), v) }
      val PunnormZeros = Punnorm
        .map { case ((i, j), value) => ((i, i), 0.0) }
        .union(Punnorm)
        .distinct()
        .partitionBy(rp)

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
        .map { case (index, arr) => (index, Vectors.dense(arr)) }
      val meanVec = Vectors.dense(rows.map(_._2.toArray).reduce((a, b) => (a, b).zipped.map(_ + _)).map(_ / sampleSize))
      // Subtract the means from each vector in the RDD
      val normalizedVecs: RDD[(Int, Vector)] = rows
        .map { case (index, vec) => (index, Vectors.dense(vec.toArray.zip(meanVec.toArray)
          .map { case (a, b) => a - b }))
        }
      val arrayData = normalizedVecs
        .collect()
        .sortWith((a, b) => a._1 < b._1)
        .map(_._2.toArray)

      val dmmat = DenseMatrix(arrayData: _*)
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
             lr: Double = 500, // learning rate
             minimumgain: Double = 0.01,
             sampleSize: Int, // number of MNIST rows, so number of points n.
             export: Boolean = false, // when true: function exports YRDD after each GD iteration.
             printing: Boolean = false, // when true: function prints all intermediate results
             takeSamples: Int, // when printing = true, the first takeSamples rows of the intermediate results are printed.
             kNNapprox: Boolean): // when true: calculate P matrix using approximation of pairwise distances.

    RDD[((Int, Int), Double)] = {

      // initialize variables
      val initVarTime = System.nanoTime()
      var momRDD: RDD[((Int, Int), Double)] = sc.parallelize(0 until sampleSize * k)
        .map { i =>
          val row = i / k
          val col = i % k
          ((row, col), 0.0)
        }
        .sortByKey()
        .partitionBy(rp)

      data.sortByKey().partitionBy(rp)


    var YRDD = sc.parallelize(0 until sampleSize * k)
      .map { i =>
        val row = i / k
        val col = i % k
        ((row, col), randn())
      }.sortByKey()
      .partitionBy(rp)


    // gains matrix that stores adaptive learning rates
    var gains = DenseMatrix.ones[Double](sampleSize, k)

      if (printing) {
        println("First n rows of YRDD after initialization: ")
        YRDD.take(takeSamples).foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))
      }


      val PRDD = computeP(data.map(_._2), n = sampleSize, kNNapprox = kNNapprox)
        .partitionBy(rp)
      PRDD.map { case ((i, j), p) => ((i, j), math.max(p, 1e-12)) }

    if (printing) {
      println("Initialization done, YRDD has dimensions: " + YRDD.map(_._1._1).reduce(math.max).toString + " x " + YRDD.map(_._1._2).reduce(math.max).toString)
      println("Is PRDD empty? " + PRDD.isEmpty().toString + ". It has dimension: " + PRDD.map(_._1._1).reduce(math.max).toString + " x " + PRDD.map(_._1._2).reduce(math.max).toString)
      println("This is PRDD: ___________________")
      PRDD.take(takeSamples).foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))
    }


    var iter: Int = 0

    while (iter < max_iter) {

      // compute gradient: dCdy_i = 4 * \sum_j (p_{ij} - q_{ij})(y_i - y_j)(1 + ||y_i - y_j||^2)^{-1}
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
      // pairwise distances
      val zippedPoints = YRDD
        .sortByKey()
        .map { case ((a, b), d) => (a, (b, d)) }
        .groupByKey()
        .mapValues(_.toArray.sortBy(_._1).map(_._2))

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
        }.partitionBy(rp).cache()

      if (printing) {
        println("____________________________________")
        println(" These are the distances for Q and num in iteration " + iter.toString)
        distances.take(takeSamples).foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))
      }

      // computation of num matrix and Q matrix from pairwise distances
      val num = distances.mapPartitions( part => part.map{ case ((i, j), d) =>
        if (i != j) {
          ((i, j), 1.0 / (1.0 + scala.math.pow(d, 2)))
        } else {
          ((i, j), d)
        }
      }, preservesPartitioning = true)

      val denom = num.map(_._2).reduce(_ + _)

      val QRDD = num
        .mapPartitions(part => part.map{ case ((i, j), d) => ((i, j), d / denom) }, preservesPartitioning = true)
      QRDD.cache()

      QRDD.mapPartitions(part => part.map{ case ((i, j), q) => ((i, j), math.max(q, 1e-12)) }, preservesPartitioning = true)

      if (printing) {
        println(" QRDD looks like this after iteration " + iter.toString + ":")
        QRDD.take(takeSamples).foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))
        println("QRDD has the following number of partitions: " + QRDD.getNumPartitions.toString)
        println("_________________________________________________")
        println("num looks like this after iteration " + iter.toString + ":")
        num.take(takeSamples).foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))
        println("num has the following number of partitions: " + num.getNumPartitions.toString)
      }

      // compute PQ matrix: p_{ij} - q_{ij}
      // output already partitioned by given partitioner
      val PQRDD = PRDD.join(QRDD).mapPartitions(part => part.map{ case ((i, j), (p, q)) => ((i, j), p - q) }, preservesPartitioning = true)
      PQRDD.cache()
      if (printing) {
        println("_________________________________________________")
        println("Is PQRDD empty in iteration number " + iter.toString + "?" + PQRDD.isEmpty().toString + ". It has dimension: " + PQRDD.map(_._1._1).reduce(math.max).toString + " x " + PQRDD.map(_._1._2).reduce(math.max).toString )
        println(" PQRDD looks like this after iteration " + iter.toString + ":")
        PQRDD.take(takeSamples).foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))
      }

      val ydiff = computeYdiff(YRDD)
        .partitionBy(rp)
      ydiff.cache()

      if (printing) {
        println("____________________________________")
        println("Is ydiff empty? " + ydiff.isEmpty().toString + ". It has dimension: " + ydiff.map(_._1._1).reduce(math.max).toString + " x " + ydiff.map(_._1._2).reduce(math.max).toString )
        println(" ydiff looks like this after iteration " + iter.toString)
        ydiff.take(takeSamples).foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2.mkString("Array(", ", ", ")")}"))
      }


      // slower implementation using join instead of cogroup
      val dCdYRDD = PQRDD.join(num).join(ydiff).map { case ((i, j), ((pq, num), diff)) => ((i, j), diff.map(_ * pq * num)) }
          .sortByKey()
          .map { case ((i, j), comp) => (i, (j, comp)) }
          .groupByKey()
          .mapValues(_.toArray.sortBy(_._1).map(_._2))
          .mapValues(arrays => arrays.reduce((a, b) => a.zip(b).map { case (x, y) => x + y }))
          .flatMap { case (i, values) => values.zipWithIndex.map { case (value, j) => ((i, j), value) } }
          .partitionBy(rp)
      dCdYRDD.cache()


      /*
      val dCdYRDD = PQRDD.cogroup(num).flatMap { case ((i, j), (pqIter, numIter)) =>
        for {
          pq <- pqIter
          num <- numIter
        } yield ((i, j), pq * num)
      }.cogroup(ydiff).flatMap { case ((i, j), (numIter, diffIter)) =>
        for {
          num <- numIter
          diff <- diffIter
        } yield ((i, j), diff.map(_ * num))
      }
        .sortByKey()
        .map { case ((i, j), comp) => (i, comp) }
        .reduceByKey((a, b) => a.zip(b).map { case (x, y) => x + y })
        .flatMap { case (i, values) => values.zipWithIndex.map { case (value, j) => ((i, j), value) } }

       */



      if (printing) {
        println("_________________________________________________")
        println("Is dCdYRDD empty? " + dCdYRDD.isEmpty().toString + ". It has dimension: " + dCdYRDD.map(_._1._1).reduce(math.max).toString + " x " + dCdYRDD.map(_._1._2).reduce(math.max).toString)
        println("dCdYRDD looks like this after iteration " + iter.toString)
        dCdYRDD.take(takeSamples).foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))
      }

      // Gradient Descent step with momentum
      val momentum = if (iter < 20) initial_momentum else final_momentum

      val dCdYBroadcast = sc.broadcast(dCdYRDD.collectAsMap())
      val momBroadcast = sc.broadcast(momRDD.collectAsMap())

      // update adaptive learning rates
      gains.foreachPair {
        case ((i, j), old_gain) =>
          val dCdYVal = dCdYBroadcast.value.getOrElse((i, j), 0.0)
          val momVal = momBroadcast.value.getOrElse((i, j), 0.0)
          val new_gain = math.max(minimumgain, if (dCdYVal > 0.0 != momVal > 0.0) old_gain + 0.2 else old_gain * 0.8)
          gains.update(i, j, new_gain)
      }

      dCdYBroadcast.unpersist()
      momBroadcast.unpersist()

      /*
      gains.foreachPair {
        case ((i, j), old_gain) =>
          val new_gain = math.max(minimumgain,
            if ((dCdYRDD.filter { case ((key1, key2), value) =>
              (key1, key2) == (i, j)
            }.map(_._2).first() > 0.0) != (momRDD.filter { case ((key1, key2), value) =>
              (key1, key2) == (i, j)
            }.map(_._2).first() > 0.0)) {
              println("filtering done")
              old_gain + 0.2
            } else
              old_gain * 0.8
          )
          gains.update(i, j, new_gain)
      }

       */

        val gainsArray = gains.toArray // Python: gains[gains < min_gain] = min_gain, set all gains that are smaller
        for (i <- gainsArray.indices) { // than min_gain to min_gain
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

      val gainsBroadcast = sc.broadcast(gains)

      momRDD = momRDD.join(dCdYRDD).map { case ((i, j), (oldderiv, currentderiv)) =>
          ((i, j), (momentum * oldderiv) - lr * (gainsBroadcast.value(i, j) * currentderiv))
        }
          .partitionBy(rp)


      if (printing) {
        println("_________________________________________________")
        println("momRDD after iteration " + iter.toString)
        momRDD.foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))
      }

      // apply update
      YRDD = YRDD.join(momRDD).map { case ((i, j), (current, update)) => ((i, j), current + update) }


      // subtract the mean of each column from each corresponding element in the column
      val YRDDColSumsMap = YRDD
        .map { case ((row, col), value) => (col, value) }
        .reduceByKey(_ + _)
        .collect()
        .toMap
      YRDD = YRDD.map { case ((i, j), y) => ((i, j), y - (YRDDColSumsMap(j) / sampleSize.toDouble)) }

      println("Finishing iteration number " + iter.toString + ".")
      println("YRDD has the following dimensions after iteration number " + iter.toString + ": " + YRDD.map(_._1._1).reduce(math.max).toString + " x " + YRDD.map(_._1._2).reduce(math.max).toString)
      println("Is YRDD empty after iteration number " + iter.toString + "? " + YRDD.isEmpty().toString)

      if (printing) {
        YRDD.take(takeSamples).foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))
      }

      iter = iter + 1

        // stop early exaggeration from iter 100 onwards
        if (iter == 100) {
          PRDD.map { case ((i, j), p) => ((i, j), p / 4.0) }
        }

      distances.unpersist()
      QRDD.unpersist()
      num.unpersist()
      PQRDD.unpersist()
      ydiff.unpersist()
      dCdYRDD.unpersist()

      gainsBroadcast.unpersist()



      // visualization
        if (export) {
//          val fs = org.apache.hadoop.fs.FileSystem.get(sc.hadoopConfiguration)
//          val path = new org.apache.hadoop.fs.Path("gs://export/")
//          if (fs.exists(path)) {
//            fs.delete(path, true)
//          }
          val exportYRDD = YRDD.coalesce(1)
            .sortByKey()
            .map { case ((i, j), d) => (i, d) }
            .sortByKey() // probably not needed
            .groupByKey()
            .sortByKey()
            .map { case (row, values) => (row, values.mkString(", ")) }
          exportYRDD.map { case (i, str) => str }.saveAsTextFile("gs://" + getNestedConfString("shellConfig", "gsBucket") + "/export/exportIter_" + iter.toString)
        }
      }
      YRDD
    }


    val sampleSize: Int = getNestedConfInt("main", "sampleSize") // SET THIS CORRECTLY

    val toRDDTime = System.nanoTime()

    val MNISTdata = sc.parallelize(
      importData("gs://" + getNestedConfString("shellConfig", "gsBucket") + getConfString("dataFileInGs"),
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

    println("Total time for this run: " + (System.nanoTime - totalTime) / 1000000 + "ms")

  }

}

