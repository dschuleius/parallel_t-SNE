import org.apache.spark.{HashPartitioner, RangePartitioner, SparkConf, SparkContext}

import scala.io.Source
import org.apache.spark.mllib.linalg.{Matrix, Vector, Vectors}
import org.apache.spark.mllib.linalg.distributed.{CoordinateMatrix, MatrixEntry, RowMatrix}
import org.apache.spark.rdd.RDD
import breeze.linalg._
import breeze.numerics.{exp, log}
import org.yaml.snakeyaml.Yaml
//import breeze.stats.mean

//import scala.collection.LazyZipOpsImplicits._ / neceassy for lazy zip in scala 2.12

import scala.util.Random

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
  // Show only Error and not Info messages
  sc.setLogLevel("ERROR")


  // function that imports MNIST from .txt files.
  def importData(fileName: String, sampleSize: Int): Array[(Int, Array[Double])] = {
    // read the file and split it into lines
//    val lines = Source.fromFile(fileName).getLines.take(sampleSize).toArray
//    Source.fromFile(fileName).close()

    val lines = Source.fromResource(fileName).getLines.take(sampleSize).toArray

    // split each line into fields and convert the fields to doubles
    // trim removes leading and trailing blank space from each field
    val data = lines
      .zipWithIndex
      .map{ case (arr, i) => (i, arr.trim.split("\\s+").map(_.toDouble)) }

    // return the data as an array of arrays of doubles
    data
      //DenseMatrix.create(data.length, data(0).length, data.flatMap(_.toArray))
  }



  def euclideanDistance(point1: Array[Double], point2: Array[Double]): Double = {
    // Calculate the squared Euclidean distance between the two points
    val squaredDistance = point1.zip(point2).map { case (x, y) => math.pow(x - y, 2) }.sum

    // Return the Euclidean distance
    math.sqrt(squaredDistance)
  }

/*
  def pairwiseDistancesFaster(points: RDD[((Int, Int), Double)]): RDD[((Int, Int), Double)] = {
    val zippedPoints = points.map { case ((i, j), d) => (i, d) }
      .groupByKey()
      .map { case (i, iterable) => (i, iterable.toArray) }
    val distances = zippedPoints.cartesian(zippedPoints)
      .flatMap {
        case ((i, u), (j, v)) =>
          if (i < j) {
            val dist = euclideanDistance(u, v)
            Seq(((i, j), dist), ((j, i), dist))
          } else if (i == j) {
            val dist = 0.0
            Seq(((i, j), dist))
          } else Seq.empty
    }
    distances
  }
 */

  def pairwiseDistancesFasterGrad(points: RDD[((Int, Int), Double)]): RDD[((Int, Int), Array[Double])] = {
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


  def computeSimilarityScoresGauss(X: RDD[Array[Double]], tol: Double = 1e-5, perplexity: Double = 30.0, n: Int, partitions: Int): RDD[((Int, Int), Double)] = {
    assert(tol >= 0, "Tolerance must be non-negative")
    assert(perplexity > 0, "Perplexity must be positive")

    val ntop = (5 * perplexity).toInt
    val logU = Math.log(perplexity)
    val norms = X.map{ arr => Vectors.norm(Vectors.dense(arr), 2.0) }
    val rowsWithNorm = X.zip(norms).map { case (v, norm) => VectorAndNorm(DenseVector(v), norm) }
    val distances = rowsWithNorm.zipWithIndex()
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
    .groupByKey()


    val p_betas =
      distances.map {
        case (i, arr) =>
          var betamin = Double.NegativeInfinity
          var betamax = Double.PositiveInfinity
          var beta = 1.0

          val d = DenseVector(arr.map(_._2).toArray)
          //println(d)
          var (h, p) = entropyBeta(d, beta)

          // evaluate if perplexity is within tolerance
          var Hdiff: Double = h - logU
          var numtries = 0

          while (Math.abs(Hdiff) > tol && numtries < 50) {
            //println("In iteration " + numtries.toString + " , beta is " + beta.toString)
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
            //println("In iteration " + numtries.toString + " , thisP is " + p.toString)
            Hdiff = h - logU
            numtries = numtries + 1
          }

          // map over the arr Array, combine the row indices with the values from p, and create a new Array of MatrixEntry objects.
          arr.map(_._1).zip(p.toArray).map { case (j, v) => MatrixEntry(i, j, v) }
      }

    val Punnorm = new CoordinateMatrix(p_betas.flatMap(me => me))
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


  /*
  def computeSimilarityScoresT(distances: RDD[((Int, Int), Double)], sampleSize: Int, partitions: Int): (RDD[((Int, Int), Double)], RDD[((Int, Int), Double)]) = {

    val num = distances.map { case ((i, j), d) =>
      if (i != j) {
        ((i, j), 1.0 / (1.0 + scala.math.pow(d, 2)))
    } else {
        ((i, j), 0.0)
      }
    }.repartition(numPartitions = partitions)

    val numsum = num.map(_._2).reduce(_ + _)

    val Q = num.map{ case ((i, j), d) => ((i, j), d / numsum)}

    (Q, num)

  }
   */

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


  /*
  def breezePCA(data: DenseMatrix[Double], reduceTo: Int = 50): Array[Array[Double]] = {

    // Calculate column mean as vector of sum of columns multiplied by 1/#rows
    // Element-wise division is not implemented as it seems, so use mult. by inverse.
    // Subtract column means from respective column entries in dataMatrix
    val meanVec = mean(data(::, *))
    val centeredData = data(*, ::) - meanVec.t

    // Compute covariance matrix (symmetric).
    val covMatrix = breeze.linalg.cov(centeredData)

    // Compute eigenvalues and eigenvectors of covariance matrix.
    val es = eigSym(covMatrix)

    val sortedEigenVectors = sortColumns(es.eigenvectors, es.eigenvalues)

    // Project data onto top k eigenvectors (change-of-basis).
    // choose top k eigenvectors
    val topEigenVectors = sortedEigenVectors(::, 0 until reduceTo)
    val projectedData = (topEigenVectors.t * centeredData.t).t


    // Convert projected data back to Array[Array[Double]]
    projectedData(*, ::).map(_.toArray).toArray
  }

   */


  // built-in PCA function
  def mlPCA(data: RDD[(Int, Array[Double])], reduceTo: Int, sampleSize: Int): RDD[(Int, Array[Double])] = {
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

    // Compute the top k principal components.
    val pc: Matrix = mat.computePrincipalComponents(reduceTo)

    val dmpc: DenseMatrix[Double] = DenseMatrix.create(pc.numRows, pc.numCols, pc.toArray)

    // project the rows to the linear space spanned by the top reduceTo principal components.
    val projected = dmmat * dmpc

    val projectedArr = (for (i <- 0 until projected.rows) yield (i, projected(i, ::).inner.toArray)).toArray

    sc.parallelize(projectedArr)
      .sortByKey()
  }



  def tSNE(data: RDD[(Int, Array[Double])], // dims already reduced using mlPCA
                 k: Int = 2, // number target dims after t-SNE has been applied to the data
                 max_iter: Int = 100,
                 initial_momentum: Double = 0.5,
                 final_momentum: Double = 0.8,
                 partitions: Int = 2, // set to number of CPU cores available
                 export: Boolean = false,
                 lr: Double = 500,
                 minimumgain: Double = 0.01,
                 sampleSize: Int):
  RDD[((Int, Int), Double)] = {

    // initialize variable
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


    var gains = DenseMatrix.ones[Double](sampleSize, k)

    println("First n rows of YRDD after initialization: ")
    YRDD.foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))

    val PRDD = computeSimilarityScoresGauss(data.map(_._2), n = sampleSize, partitions = partitions).partitionBy(new RangePartitioner(partitions = partitions, momRDD))
    PRDD.map { case ((i, j), p) => ((i, j), math.max(p, 1e-12))}
    println("Initialization done, YRDD has dimensions: " + YRDD.map(_._1._1).reduce(math.max).toString + " x " + YRDD.map(_._1._2).reduce(math.max).toString)
    println("Is PRDD empty? " + PRDD.isEmpty().toString +". It has dimension: " + PRDD.map(_._1._1).reduce(math.max).toString + " x " + PRDD.map(_._1._2).reduce(math.max).toString )
    //println("This is PRDD: ___________________")
    //PRDD.foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))



    var iter: Int = 0

    while (iter < max_iter) {

      // compute gradient: insert into every row of dCdy_i = 4 * \sum_j (p_{ij} - q_{ij})(y_i - y_j)(1 + ||y_i - y_j||^2)^{-1}
      // see equation (5) in the original paper: https://jmlr.org/papers/volume9/vandermaaten08a/vandermaaten08a.pdf
      // y points are points in the low-dim space that are moved into clusters by the optimization.

      println("_____________________________________________")
      println("_____________________________________________")
      println("_____________________________________________")
      println("Starting iteration number " + iter.toString + " with YRDD that looks like this:")
      YRDD.take(10).foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))

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
      //println("____________________________________")
      //println(" These are the distances for Q and num in iteration " + iter.toString)
      //distances.foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))

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

      //println(" QRDD looks like this after iteration " + iter.toString + "_________")
      //QRDD.foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))
      //println("_________________________________________________")
      //println("num looks like this after iteration " + iter.toString + "_________")
      //num.foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))


      val PQRDD = PRDD.join(QRDD).map { case ((i, j), (p, q)) => ((i, j), p - q) }
        .partitionBy(new RangePartitioner(partitions = partitions, momRDD))
      PQRDD.cache()
      //println("_________________________________________________")
      //println("Is PQRDD empty in iteration number " + iter.toString + "?" + PQRDD.isEmpty().toString + ". It has dimension: " + PQRDD.map(_._1._1).reduce(math.max).toString + " x " + PQRDD.map(_._1._2).reduce(math.max).toString )
      //println(" PQRDD looks like this after iteration " + iter.toString + "_________")
      //PQRDD.foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))


      val ydiff = pairwiseDistancesFasterGrad(YRDD)
        .partitionBy(new RangePartitioner(partitions = partitions, momRDD))
      ydiff.cache()
      //println("Is ydiff empty? " + ydiff.isEmpty().toString + ". It has dimension: " + ydiff.map(_._1._1).reduce(math.max).toString + " x " + ydiff.map(_._1._2).reduce(math.max).toString )
      //println(" ydiff looks like this after iteration " + iter.toString)
      //ydiff.foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2.mkString("Array(", ", ", ")")}"))


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
      //dCdYRDD.foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))


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

      //println("_________________________________________________")
      //println("gains in iteration " + iter.toString)
      //println(gains)

      //println("_________________________________________________")
      //println("momRDD in iteration " + iter.toString + " BEFORE UPDATE WITH CURRENTDERIV")
      //momRDD.foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))


      momRDD = momRDD.join(dCdYRDD).map{ case ((i, j), (oldderiv, currentderiv)) =>
        ((i, j), (momentum * oldderiv) - lr * (gains(i, j) * currentderiv)) }
        .sortByKey()
        .partitionBy(new RangePartitioner(partitions = partitions, momRDD))

      //println("_________________________________________________")
      //println("momRDD after iteration " + iter.toString)
      //momRDD.foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))

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
      //YRDD.foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))

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
        val exportYRDD = YRDD.coalesce(1)
          .sortByKey()
          .map{ case ((i, j), d) => (i, d)}
          .sortByKey()
          .groupByKey()
          .sortByKey()
          .map{ case (row, values) => (row, values.mkString(", ")) }
        exportYRDD.map{ case (i, str) => str }.saveAsTextFile("data/exportIter_" + iter.toString)
      }
    }
    YRDD
  }


  // Define main function
  def main(args: Array[String]): Unit = {
    val sampleSize: Int = getNestedConfInt("main", "sampleSize") // SET THIS CORRECTLY
    val partitions: Int = getNestedConfInt("main", "partitions")

    val toRDDTime = System.nanoTime()


    // import numpy array of MNIST values, first 1000 rows, that have been reduced to dim 50 using PCA
//    val MNISTpca_n1000_k50 = sc.parallelize(importData("MNISTpca_n1000_k50", sampleSize))
//      .sortByKey()

    val MNISTdata = sc.parallelize(
      importData(getConfString("dataFile"),
      getNestedConfInt("main", "sampleSize")))


    //val MNISTdata = sc.parallelize(importData("mnist2500_X.txt", sampleSize)) // only filename, no path
    println("To RDD time for " + sampleSize + " samples: " + (System.nanoTime - toRDDTime) / 1000000 + "ms")

    // testing with small dataset
    /*
    val testX = Array((0, Array(1.0, 2.0, 0.8)),(1, Array(3.0, 4, 12.3)), (2, Array(5.0, 6, 2.2)), (3, Array(7.0, 8, 8.7)), (4, Array(9.0, 10, 1.0)))
    val testXRDD = sc.parallelize(testX)
    */

//    val MNIST_mlPCA = mlPCA(MNISTdata, sampleSize = sampleSize)
    val MNIST_mlPCA = mlPCA(
      data = MNISTdata,
      sampleSize = getNestedConfInt("main", "sampleSize"),
      reduceTo = getNestedConfInt("mlPCA", "reduceTo"),
    )
    println("__________________________________")
    println("This is the data after applying mlPCA:")
    MNIST_mlPCA.foreach(t => println(t._1 + " " + t._2.mkString(" ")))

    // testing tSNEsimple
    val totalTime = System.nanoTime()

//    val YmatOptimized = tSNE(MNIST_mlPCA, sampleSize = sampleSize, max_iter = 2, `export` = true)
    val YmatOptimized = tSNE(
      data = MNIST_mlPCA,
      k = getNestedConfInt("tSNE", "k"),
      max_iter = getNestedConfInt("tSNE", "max_iter"),
      initial_momentum = getNestedConfDouble("tSNE", "initial_momentum"),
      final_momentum = getNestedConfDouble("tSNE", "final_momentum"),
      partitions = getNestedConfInt("tSNE", "partitions"),
      export = getNestedConfBoolean("tSNE", "export"),
      lr = getNestedConfDouble("tSNE", "lr"),
      minimumgain = getNestedConfDouble("tSNE", "minimumgain"),
      sampleSize = getNestedConfInt("main", "sampleSize"),
    )

    println("_______________________________________")
    println("ENDRESULT YRDD:")
    YmatOptimized.foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))





    // TESTING ____________________


    /*
   var testYRDD = sc.parallelize(0 until 5 * 2)
     .map { i =>
       val row = i / 2
       val col = i % 2
       ((row, col), List(1.0, 2, 3, 4, 5, 6, 7, 8, 9, 10)(i))
     }



  val PRDDt = computeSimilarityScoresGauss(testXRDD, n = 5)
  println("______________________________________________")
  println("PRDDt looks like this: ______________________")
  PRDDt.foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))

  val QRDDt = computeSimilarityScoresT(pairwiseDistancesFaster(testYRDD), sampleSize = 5, partitions = 2)._1
  println("______________________________________________")
  println("QRDDt looks like this: ______________________")
  QRDDt.foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))

  val PQRDDt = PRDDt.join(QRDDt).map { case ((i, j), (p, q)) => ((i, j), p - q) }
  println("______________________________________________")
  println("PQRDDt looks like this: ______________________")
  PQRDDt.foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))

  val ydifft = pairwiseDistancesFasterGrad(testYRDD, k = 2).partitionBy(new HashPartitioner(partitions = 2))
  println("______________________________________________")
  println("ydifft looks like this: ______________________")
  ydifft.foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2.mkString("Array(", ", ", ")")}"))

  val numt = computeSimilarityScoresT(pairwiseDistancesFaster(testYRDD), sampleSize = 5, partitions = 2)._2

  val dCdYRDDt = PQRDDt.join(numt).join(ydifft).map { case ((i, j), ((pq, num), diff)) => ((i, j), diff.map(_ * pq * num)) }
    .map { case ((i, j), comp) => (i, comp) }
    .groupByKey()
    .mapValues(arrays => arrays.reduce((a, b) => a.zip(b).map { case (x, y) => x + y }))
    .flatMap { case (key, values) => values.zipWithIndex.map { case (value, index) => ((key, index), value) } }
  println("______________________________________________")
  println("dCdYRDDt looks like this: ______________________")
  dCdYRDDt.foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))

  val momentumt = 0.5
  val minimumgaint = 0.01
  val eta = 500

  var momRDDt: RDD[((Int, Int), Double)] = sc.parallelize(0 until 5 * 2)
    .map { i =>
      val row = i / 2
      val col = i % 2
      ((row, col), 0.0)
    }.partitionBy(new HashPartitioner(partitions = 2))

  val gainst = DenseMatrix.ones[Double](5, 2)
  gainst.foreachPair {
    case ((i, j), old_gaint) =>
      val new_gain = math.max(minimumgaint,
        if ((dCdYRDDt.filter { case ((key1, key2), value) =>
          (key1, key2) == (i, j)
        }.map(_._2).first() > 0.0) != (momRDDt.filter { case ((key1, key2), value) =>
          (key1, key2) == (i, j)
        }.map(_._2).first() > 0.0))
          old_gaint + 0.2
        else
          old_gaint * 0.8
      )
      gainst.update(i, j, new_gain)
  }
  println("______________________________________________")
  println("gains looks like this: ______________________")
  println(gainst)



  momRDDt = momRDDt.join(dCdYRDDt).map { case ((i, j), (oldderiv, currentderiv)) => ((i, j), momentumt * oldderiv - eta * (gainst(i, j) * currentderiv)) }
  println("______________________________________________")
  println("momRDDt looks like this: ______________________")
  momRDDt.foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))

  testYRDD = testYRDD.join(momRDDt).map { case ((i, j), (current, update)) => ((i, j), current + update) }
  //println("YRDD looks like this BEFORE centering:")
  //YRDD.foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))


  // subtract the mean of each column from each corresponding element in the column
  val YRDDColSumsMap = testYRDD
    .map { case ((row, col), value) => (col, value) }
    .reduceByKey(_ + _)
    .collect()
    .toMap
  //println("YRDDColSums (iteration: " + iter.toString + " ) looks like this: ")
  testYRDD = testYRDD.map { case ((i, j), y) => ((i, j), y - (YRDDColSumsMap(j) / 5.0)) }
  println("______________________________________________")
  println("testYRDD looks like this: ______________________")
  testYRDD.foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))
  */


    println("Total time: " + (System.nanoTime - totalTime) / 1000000 + "ms")
  }

}

