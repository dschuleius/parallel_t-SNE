// https://spark.apache.org/docs/latest/rdd-programming-guide.html
// Create package
import org.apache.spark.{HashPartitioner, SparkConf, SparkContext}

import scala.io.Source
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.linalg.distributed.{CoordinateMatrix, MatrixEntry, RowMatrix}
import org.apache.spark.rdd.RDD
import breeze.linalg._
import breeze.numerics.{exp, log}
import org.apache.spark.mllib.rdd.MLPairRDDFunctions.fromPairRDD

import java.io.File
import scala.util.Random

object ScalaImplementation {

  // set up Spark, changing to local host.
  val conf: SparkConf = new SparkConf()
    .setAppName("distributed_t-SNE")
    .setMaster("local[*]")
    .set("spark.driver.host", "127.0.0.1")
    .set("spark.driver.bindAddress", "127.0.0.1")
    //.set("spark.sql.shuffle.partitions", "2")
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


  // testing
  println("Testing data import:")
  //MNISTdata.take(10).foreach(println)

  def euclideanDistance(point1: Array[Double], point2: Array[Double]): Double = {
    // Calculate the squared Euclidean distance between the two points
    val squaredDistance = point1.zip(point2).map { case (x, y) => (x - y) * (x - y) }.sum

    // Return the Euclidean distance
    Math.sqrt(squaredDistance)
  }


  def pairwiseDistancesFaster(points: RDD[((Int, Int), Double)]): RDD[((Int, Int), Double)] = {
    val zippedPoints = points.map { case ((i, j), d) => (i, d) }
      .groupByKey()
      .map { case (i, iterable) => (i, iterable.toArray) }
    val distances = zippedPoints.cartesian(zippedPoints)
      .flatMap {
        case ((i, u), (j, v)) =>
          if (i < j) {
            val dist = euclideanDistance(u, v)
            Seq(((i.toInt, j.toInt), dist), ((j.toInt, i.toInt), dist))
          } else if (i == j) {
            val dist = 0.0
            Seq(((i.toInt, j.toInt), dist))
          } else Seq.empty
    }
    distances
  }

  def pairwiseDistancesFasterGrad(points: RDD[((Int, Int), Double)], k: Int): RDD[((Int, Int), Array[Double])] = {
    val zippedPoints = points.map{ case ((i, j), d) => (i, d)}
      .groupByKey()
      .map{ case (i, iterable) => (i, iterable.toArray)}
    val differences = zippedPoints.cartesian(zippedPoints)
      .flatMap {
        case ((i, u), (j, v)) =>
          if (i < j) {
            val dist = u.zip(v).map{ case (a,b) => a - b}
            Seq(((i.toInt, j.toInt), dist), ((j.toInt, i.toInt), dist))
          //} else if (i == j) {
          //  Seq(((i.toInt, i.toInt), Array.fill(k)(0.0)))
        } else Seq.empty
      }
    differences
  }

  println("Testing pairwiseDistances function: ")
  //println(pairwiseDistances(MNISTdata))


  /*
  // calculates n x n entries
  def computeSimilarityScoresGauss(distances: RDD[((Int, Int), Double)],
                                   sigma: Double,
                                   n: Int): RDD[((Int, Int), Double)] = {

    val unnormSimilarities = distances.map { case ((i, j), d) =>
      ((i, j), math.exp(-1 * scala.math.pow(d, 2) / (2 * scala.math.pow(sigma, 2))))
    }

    val denominators = unnormSimilarities.flatMap {
      case ((i, j), d) =>
        if (i != j) {
          Seq(((i, j), math.exp(-1.0 * scala.math.pow(d, 2) / (2 * scala.math.pow(sigma, 2)))))
        } else {
          Seq(((i, j), 1.0))
        }
    }

      /*
      .filter { case ((i, k), _) => i != k }.map { case ((i, j), d) =>
      ((i, j), math.exp(-1 * scala.math.pow(d, 2) / (2 * scala.math.pow(sigma, 2))))
    }.reduceByKey(_ + _)
       */

    val unnormSimilaritiesWithDenominator = unnormSimilarities.join(denominators).map { case ((i, j), (unnorm, denominator)) =>
      ((i, j), unnorm / denominator)
    }

    val flippedUnnormSimWithDenom = unnormSimilaritiesWithDenominator.map(x => (x._1.swap, x._2))
    val joinedUnnormSimWithDenom = unnormSimilaritiesWithDenominator.join(flippedUnnormSimWithDenom)
    val normSimilarities = joinedUnnormSimWithDenom.mapValues { case (s1, s2) => (s1 + s2) / (2 * n) }


    normSimilarities
  }

   */


  // testing computeSimilarityScoreGauss
  println("Testing computeSimilarityGauss function:")
  //println(computeSimilarityScoresGauss(pairwiseDistances(MNISTdata), sigma = 1))

  case class VectorAndNorm(vector: Vector[Double], norm: Double)


  def Hbeta(D: DenseVector[Double], beta: Double = 1.0): (Double, DenseVector[Double]) = {
    val P: DenseVector[Double] = exp(-D * beta) // beta = 1 / 2 * sigma_i
    val sumP = sum(P)
    if (sumP == 0) {
      (0.0, DenseVector.zeros(D.size))
    } else {
      val H = log(sumP) + (beta * sum(D *:* P) / sumP)
      (H, P / sumP)
    }
  }


  def computeSimilarityScoresGauss(X: RDD[Array[Double]], tol: Double = 1e-5, perplexity: Double = 30.0): RDD[((Int, Int), Double)] = {
    require(tol >= 0, "Tolerance must be non-negative")
    require(perplexity > 0, "Perplexity must be positive")

    val ntop = (3 * perplexity).toInt
    val logU = Math.log(perplexity)
    val norms = X.map{ case (arr) => Vectors.norm(Vectors.dense(arr), 2.0)}
    val rowsWithNorm = X.zip(norms).map { case (v, norm) => VectorAndNorm(DenseVector(v), norm) }
    val distances = rowsWithNorm.zipWithIndex()
      .cartesian(rowsWithNorm.zipWithIndex())
      .flatMap {
        case ((u, i), (v, j)) =>
          if (i < j) {
            val dist = euclideanDistance(u.vector.toArray, v.vector.toArray)
            Seq((i, (j, dist)), (j, (i, dist)))
          } else Seq.empty
      }.topByKey(ntop) //returns the top k (largest) elements for each key from this RDD


    val p_betas =
      distances.map {
        case (i, arr) =>
          var betamin = Double.NegativeInfinity
          var betamax = Double.PositiveInfinity
          var beta = 1.0

          val d = DenseVector(arr.map(_._2))
          var (h, p) = Hbeta(d, beta)

          // evaluate if perplexity is within tolerance
          def Hdiff: Double = h - logU

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
            val (h, p) = Hbeta(d, beta)

            numtries = numtries + 1
          }

          // map over the arr Array, combine the row indices with the values from p, and create a new Array of MatrixEntry objects.
          (arr.map(_._1).zip(p.toArray).map { case (j, v) => MatrixEntry(i, j, v) }, beta)
      }

    new CoordinateMatrix(p_betas.flatMap(_._1)).entries.map(entry => ((entry.i.toInt, entry.j.toInt), entry.value))

  }




  // returns a tuple of 2 RDDs, "num" containing the numerator values, which are later needed for the gradient
  // computation, "normSimilarities" containing the Similarity Scores in the low-dim. representation.
  // computes n x n entries
  def computeSimilarityScoresT(distances: RDD[((Int, Int), Double)], sampleSize: Int): (RDD[((Int, Int), Double)], RDD[((Int, Int), Double)]) = {

    // println("computeSimilarityScoresT started with input distances matrix of dimensions: " + distances.map(_._1._1).reduce(math.max).toString + " x " + distances.map(_._1._2).reduce(math.max).toString)

    val num = distances.map { case ((i, j), d) =>
      ((i, j), (1.0 / (1 + scala.math.pow(d, 2))))
    }

    val denominators = num.flatMap {
      case ((i, j), d) =>
        if (i != j) {
          Seq(((i, j), (1.0 / 1.0 + scala.math.pow(d, 2))))
        } else {
          Seq(((i, j), 1.0))
        }
    }

    val unnormSimilaritiesWithDenominator = num.join(denominators).map { case ((i, j), (unnorm, denominator)) =>
      ((i, j), unnorm / denominator)
    }

    val flippedUnnormSimWithDenom = unnormSimilaritiesWithDenominator.map(x => (x._1.swap, x._2))
    val joinedUnnormSimWithDenom = unnormSimilaritiesWithDenominator.join(flippedUnnormSimWithDenom)
    val normSimilarities = joinedUnnormSimWithDenom.mapValues { case (s1, s2) => (s1 + s2) / (2 * sampleSize) }


    (normSimilarities, num)

  }



  // built-in PCA function
  def mlPCA(data: RDD[Array[Double]], k: Int = 50): RDD[Array[Double]] = {
    val rows = data.map(x => Vectors.dense(x))
    val mat = new RowMatrix(rows)
    println("Checkpoint 1: Matrix creation")
    // Compute the top k principal components.
    // Principal components are stored in a local dense matrix.
    val pc = mat.computePrincipalComponents(k)
    println("Checkpoint 2: PC")

    // Project the rows to the linear space spanned by the top 4 principal components.
    val projected: RowMatrix = mat.multiply(pc)
    println("Checkpoint 3: projected")

    val projectedRDD: RDD[Array[Double]] = projected.rows.map(_.toArray)
    println("Checkpoint 4: projectedRDD")

    projectedRDD
  }

  println("Testing builtin PCA function:")
  //println(mlPCA(MNISTdata).foreach(arr => println(arr.mkString(","))))

  /*
  def stackVector(vector: DenseVector[Double], n: Int): DenseMatrix[Double] = {
    DenseMatrix.tabulate[Double](n, vector.size)((i, j) => vector(j))
  }
  */




  // GD optimization is inherently sequential, hence we use a DenseMatrix collection to handle the data.
  def tSNEsimple(X: RDD[Array[Double]], // dims already reduced using mlPCA
                 k: Int = 2, // number target dims after t-SNE has been applied to the data
                 max_iter: Int = 200,
                 partitions: Int = 2,
                 export: Boolean = false,
                 lr: Double = 500,
                 sampleSize: Int = 10):
  RDD[((Int, Int), Double)] = {

    // initialize variable
    val initVarTime = System.nanoTime()
    val n: Int = sampleSize
    // val diagonalRDD = sc.parallelize((0 until n).map(i => (i, i) -> 1.0))
    val rand = new Random(123)
    val YRDDnotparallel = (0 until n).map{ _ => Array.tabulate(k)( _ => rand.nextGaussian()) } // initialize randomly from standard gaussian, issue SysTime

    var YRDD = sc.parallelize(0 until n * k)
      .map { i =>
        val row = i / k
        val col = i % k
        ((row, col), randn())
      }

    YRDD.take(10).foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))

    // YRDD.foreach(arr => println(arr.mkString(",")))
    val PRDD = computeSimilarityScoresGauss(X).partitionBy(new HashPartitioner(partitions = partitions))
    println("Initialization done, YRDD has dimensions: " + YRDD.map(_._1._1).reduce(math.max).toString + " x " + YRDD.map(_._1._2).reduce(math.max).toString)
    println("Is PRDD empty? " + PRDD.isEmpty().toString +". It has dimension: " + PRDD.map(_._1._1).reduce(math.max).toString + " x " + PRDD.map(_._1._2).reduce(math.max).toString )

    var iter: Int = 0

    while (iter < max_iter) {

      // compute gradient: insert into every row of dCdy_i = 4*sum_j(p_ij - q_ij)(y_i - y_j) * (1 + L2)^-1
      // see equation (5) in the original paper: https://jmlr.org/papers/volume9/vandermaaten08a/vandermaaten08a.pdf
      // y points are points in the low-dim space that are moved into clusters by the optimization.

      println("Starting iteration number " + iter.toString + ".")

      val simscoresYRDD = computeSimilarityScoresT(pairwiseDistancesFaster(YRDD), n)
      val QRDD = simscoresYRDD._1.partitionBy(new HashPartitioner(partitions = partitions))
      val num = simscoresYRDD._2.partitionBy(new HashPartitioner(partitions = partitions))
      QRDD.cache()
      num.cache()

      println("QRDD and num done for iteration number " + iter.toString + ".")

      val PQRDD = PRDD.join(QRDD).map { case ((i, j), (p, q)) => ((i, j), (p - q)) }
      println("Is PQRDD empty in iteration number " + iter.toString + "?" + PQRDD.isEmpty().toString + ". It has dimension: " + PQRDD.map(_._1._1).reduce(math.max).toString + " x " + PQRDD.map(_._1._2).reduce(math.max).toString )
      PQRDD.cache()

      val ydiff = pairwiseDistancesFasterGrad(YRDD, k = k).partitionBy(new HashPartitioner(partitions = partitions))
      println("Is ydiff empty? " + ydiff.isEmpty().toString + ". It has dimension: " + ydiff.map(_._1._1).reduce(math.max).toString + " x " + ydiff.map(_._1._2).reduce(math.max).toString )
      ydiff.cache()


      val dCdYRDD = PQRDD.join(num).join(ydiff).map{ case ((i, j), ((pq, num), diff)) => ((i, j), diff.map(_ * 4.0 * pq * num))}
        .map{ case ((i, j), comp) => (i, comp)}
        .groupByKey()
        .mapValues(arrays => arrays.reduce((a, b) => a.zip(b).map{ case (x, y) => x + y }))
        .flatMap{ case (key, values) => values.zipWithIndex.map{ case (value, index) => ((key, index), value) } }

      dCdYRDD.cache()
      println("Is dCdYRDD empty? " + dCdYRDD.isEmpty().toString +". It has dimension: " + dCdYRDD.map(_._1._1).reduce(math.max).toString + " x " + dCdYRDD.map(_._1._2).reduce(math.max).toString )
      dCdYRDD.take(10).foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))


      // Gradient Descent step without gain and momentum
      YRDD = YRDD.join(dCdYRDD).map { case ((i, j), (current, update)) => ((i, j), current - lr * update) }

      println("Finishing iteration number " + iter.toString + ".")
      println("YRDD has the following dimensions after iteration number " + YRDD.map(_._1._1).reduce(math.max).toString + " x " + YRDD.map(_._1._2).reduce(math.max).toString)
      println("Is YRDD empty after iteration number " + iter.toString + "? " + YRDD.isEmpty().toString)
      YRDD.take(10).foreach(entry => println(s"(${entry._1._1}, ${entry._1._2}) = ${entry._2}"))


      iter = iter + 1

      //simscoresYRDD.unpersist()
      QRDD.unpersist(blocking = true)
      num.unpersist(blocking = true)
      PQRDD.unpersist(blocking = true)
      ydiff.unpersist(blocking = true)
      dCdYRDD.unpersist(blocking = true)

      // visualization: collect YRDD and transform to DenseMatrix to be able to print:
      // TODO this only produces one column instead of 2
      if (export) {
        val exportYRDD = YRDD.coalesce(1).groupByKey().map{ case (key, values) => (key, values.mkString(", ")) }
        exportYRDD.map{ case ((i, j), str) => str }.saveAsTextFile("data/exportYRDD_" + iter.toString + ".txt")
      }


      /*
      for (i <- 0 until n) {
        dCdY(i, ::) := sum(tile(PQmat(::, i) *:* nummat(::, i), 1, k) *:* (stackVector(Ymat(i, ::).t, n) - Ymat), Axis._0)
      }


      val momentum = if (iter <= 20) initial_momentum else final_momentum

      gains.map {
        case ((i, j), old_gain) =>
          val new_gain = math.max(min_gain,
            if ((dCdY(i, j) > 0.0) != (iY(i, j) > 0.0))
              old_gain + 0.2
            else
              old_gain * 0.8
          )
          ((i, j), new_gain)
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

          */

    }
    YRDD
  }


  // Define main function
  def main(args: Array[String]): Unit = {
    val sampleSize: Int = 100

    val toRDDTime = System.nanoTime()
    val MNISTdata: RDD[Array[Double]] = sc.parallelize(importData("/Users/juli/Documents/WiSe_2223_UniBo/ScalableCloudProg/parralel_t-SNE/data/mnist2500_X.txt", sampleSize))
    println("To RDD time for " + sampleSize + " samples: " + (System.nanoTime - toRDDTime) / 1000000 + "ms")

    for {
      files <- Option(new File("data/").listFiles)
      file <- files if file.getName.startsWith("exportYRDD_")
    } file.delete()

    // testing tSNEsimple
    val totalTime = System.nanoTime()
    val MNISTdataPCA = mlPCA(MNISTdata)

    val YmatOptimized = tSNEsimple(MNISTdataPCA, sampleSize = sampleSize, max_iter = 10, `export` = false)

    println("Total time: " + (System.nanoTime - totalTime) / 1000000 + "ms")
  }

  // TODO visualization: wrong results, x and y values equal for all points
  // TODO logger function, code cleanup, .jar SBT build
}

