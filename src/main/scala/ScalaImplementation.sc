// This file contains an implementation of t-SNE using plain Scala without parallel data collections

import scala.io.Source
import breeze.linalg._
import breeze.stats.mean
import breeze.util.JavaArrayOps.dmToArray2


// handle MNIST file import, take first 'sampleSize' entries.
def importData(fileName: String, sampleSize: Int): Array[Array[Double]] = {
  // Read the file and split it into lines
  val lines = Source.fromFile(fileName).getLines.take(sampleSize).toArray

  // Split each line into fields and convert the fields to doubles
  // trim removes leading and trailing blank space from each field
  val data = lines.map(_.trim.split("\\s+").map(_.toDouble))

  // Return the data as an array of arrays of doubles
  data
}

// testing importData
// relative path does not work, probably problem with SBT folder structure
val MNISTlabels = importData("/Users/juli/Documents/WiSe_2223_UniBo/ScalableCloudProg/parralel_t-SNE/data/mnist2500_labels.txt", 10)
val MNISTdata = importData("/Users/juli/Documents/WiSe_2223_UniBo/ScalableCloudProg/parralel_t-SNE/data/mnist2500_X.txt", 10)


// ----------------------------
// HELPER FUNCTIONS for L2 distance and Cov-Matrix
def euclideanDistance(point1: Array[Double], point2: Array[Double]): Double = {
  // Calculate the squared Euclidean distance between the two points
  val squaredDistance = point1.zip(point2).map { case (x, y) => (x - y) * (x - y) }.sum

  // Return the Euclidean distance
  Math.sqrt(squaredDistance)
}


// sortColumns and subtractVectorFromMatrix helper functions for PCA function
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




// testing sortColumns
// DenseMatrix constructor takes in Lists as rows!!
val B: DenseMatrix[Double] = DenseMatrix((1.1, 20.1, 311.1), (1.0, 20.1, 300.11), (1.0, 20.0, 303.0))
val Bvec: DenseVector[Double] = DenseVector(-1, 10, 200)
println(sortColumns(B, Bvec))
// seems to work well


// subtracts a DenseVector from every column of a DenseMatrix: Functional Programming Style
def subtractVectorFromMatrix(mat: DenseMatrix[Double], vec: DenseVector[Double]): DenseMatrix[Double] = {
  mat(*, ::).map(row => (row - vec))
}

// testing subtractVectorFromMatrix
val SVFM: DenseMatrix[Double] = DenseMatrix((1.0, 5.0, 10.0), (2.0, 6.0, 12.0), (3.0, 7.0, 14.0))
val SVFMvec: DenseVector[Double] = DenseVector(5.0, 5.0, 5.0)
println(subtractVectorFromMatrix(SVFM, SVFMvec))



def stackVector(vector: DenseVector[Double], n: Int): DenseMatrix[Double] = {
  DenseMatrix.tabulate[Double](n, vector.size)((i, j) => vector(j))
}

// testing stackVector
val vector = DenseVector(1.0, 2.0, 3.0)
val n = 3
val result = stackVector(vector, n)
println(result)


// ----------------------------




def calculatePairwiseDistances(points: Array[Array[Double]]): Array[Array[Double]] = {
  // initialize the distance matrix
  val distances = Array.ofDim[Double](points.length, points.length)

  // calculate the pairwise distances between all points
  for (i <- points.indices; j <- points.indices) {
    if (i < j) {
      // calculate only right upper triangle and duplicate values
      distances(i)(j) = euclideanDistance(points(i), points(j))
      distances(j)(i) = distances(i)(j)
      // set diagonal to 0
      distances(i)(i) = 1
    }
  }

  // Return the distance matrix
  distances
}


/*
// rewrite calculatePairwiseDistances into functional programming style
def calculatePairwiseDistancesFP(points: DenseMatrix[Double]): DenseMatrix[Double] = {
  val n = points.rows
  val indices = 0 until n
  val dist = DenseMatrix.tabulate[Double](n, n){ case (i, j) =>
    if (i < j) {
      euclideanDistance(points(i, ::).t.toArray, points(j, ::).t.toArray)
    } else if {
      0.0
    }
  }
  dist
}
*/


// testing of calculatePairwiseDistances function
val X: Array[Array[Double]] = Array(Array(1.2, 3.4, 10.2), Array(10.4, 22.3, 4.2))
val XPDmat: DenseMatrix[Double] = DenseMatrix((1.2, 3.4, 10.2),(10.4, 22.3, 4.2))
println(euclideanDistance(point1 = Array(1, 1.2, 2), point2 = Array(10, 11, 12)))
println(calculatePairwiseDistances(X)(0)(1))
// println(calculatePairwiseDistancesFP(XPDmat))
// works as intended, checked with Python



// computeSimilarityScoresGauss calculates SimilarityScores for the high-dim. representation of the data
// only implemented with CONSTANT SIGMA so far
// to add: Perplexity => adaptive Sigma
def computeSimilarityScoresGauss(distances: Array[Array[Double]], sigma: Double): Array[Array[Double]] = {
  // check that distance matrix is symmetric, i.e. n x n, otherwise throw error
  assert(distances.length == distances(0).length, "Distance-Matrix is not symmetric.")
  val n = distances.length
  val unnormSimilarities = Array.ofDim[Double](n, n)
  val normSimilarities = Array.ofDim[Double](n, n)
  for (i <- 0 until n) {
    for (j <- 0 until n) {
      // use yield to obtain "buffered for-loop" that returns all collection of all yielded values.
      unnormSimilarities(i)(j) = {
        math.exp(-1 * scala.math.pow(distances(i)(j), 2) / (2 * scala.math.pow(sigma, 2))) /
          (for (k <- 0 until n if k != i) yield math.exp(-1 * scala.math.pow(distances(i)(k), 2) /
            (2 * scala.math.pow(sigma, 2)))).sum
      }
    }
  }
  // average the two similarity scores from p-th to q-th point and from q-th to p-th point.
  // sim. scores might differ, as different perplexity and thus sigma is used for Gauss. kernel.
  for (i <- 0 until n) {
    for (j <- 0 until n) {
      normSimilarities(i)(j) = (unnormSimilarities(i)(j) + unnormSimilarities(j)(i)) / (2 * n)
    }
  }
  normSimilarities
}

// testing of computeSimilarityScoreGauss function
println(computeSimilarityScoresGauss(distances = calculatePairwiseDistances(MNISTdata), sigma = 1)(5)(4))



// computeSimilarityScoresT calculates SimilarityScores for low-dim. representation of the data (after PCA)
// returns a tuple consisting of the multi-dim. Array with all SimilarityScores and a multi-dim. Array "num"
// that is needed for the gradient calculation and handy to have
def computeSimilarityScoresT(distances: Array[Array[Double]]): (Array[Array[Double]], Array[Array[Double]]) = {
  // check that distance matrix is symmetric, i.e. n x n, otherwise throw error
  assert(distances.length == distances(0).length, "Distance-Matrix is not symmetric.")
  val n = distances.length
  val unnormSimilarities = Array.ofDim[Double](n, n)
  val num = Array.ofDim[Double](n, n)
  val normSimilarities = Array.ofDim[Double](n, n)
  for (i <- 0 until n) {
    for (j <- 0 until n) {
      // use yield to obtain "buffered for-loop" that returns all collection of all yielded values.
      num(i)(j) = (1.0/ (1 + scala.math.pow(distances(i)(j), 2)))
      unnormSimilarities(i)(j) = {
        num(i)(j) /
          1.0 / (for (k <- 0 until n if k != i) yield 1 + scala.math.pow(distances(i)(k), 2)).sum
      }
    }
  }
  // average the two similarity scores from p-th to q-th point and from q-th to p-th point.
  // sim. scores might differ, as different perplexity and thus sigma is used for Gauss. kernel.
  for (i <- 0 until n) {
    for (j <- 0 until n) {
      normSimilarities(i)(j) = (unnormSimilarities(i)(j) + unnormSimilarities(j)(i)) / (2 * n)
    }
  }
  (normSimilarities, num)
}




// obtain first lower dimensional representation of points using PCA
def pca(data: Array[Array[Double]], k: Int): Array[Array[Double]] = {
  // assert non-empty Array and no empty rows
  if (data.isEmpty || data.exists(_.isEmpty)) {
    throw new IllegalArgumentException("Data array cannot be empty or contain empty rows")
  }

  // assert symmetric multi-dim Array
  if (data.map(_.length).distinct.length > 1) {
    throw new IllegalArgumentException("Rows in data array must have the same number of columns")
  }

  // Convert data to breeze DenseMatrix
  val dataMatrix = DenseMatrix(data.map(row => DenseVector(row)): _*)

  // Calculate column mean as vector of sum of columns multiplied by 1/#rows
  // Element-wise division is not implemented as it seems, so use mult. by inverse.
  // Subtract column means from respective column entries in dataMatrix
  val meanVector = sum(dataMatrix(::, *)) *:* (1.0/dataMatrix.rows.toDouble)
  val centeredDataMatrix = subtractVectorFromMatrix(dataMatrix, meanVector.t)

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
  val projDataArray = dmToArray2(projectedData)

  projDataArray
}


// testing pca function
val pcaMNISTdata: Array[Array[Double]] = pca(MNISTdata, k = 2)
// result is correct but sign of 2nd principal comp. score is switched
// no problem though, see
// https://stats.stackexchange.com/questions/30348/is-it-acceptable-to-reverse-a-sign-of-a-principal-component-score
println(computeSimilarityScoresT(distances = calculatePairwiseDistances(pcaMNISTdata))._1(0)(1))
// seems to work as well



// optimization using GD, making SimilarityScore matrices P (high-dim) and Q (low-dim) as similar as possible.
// we use the gradient etc. described in the "Symmetric SNE" section of the original paper.
// !! SCOPING OF VARS STILL WRONG: IN EVERY ITERATION, Y IS BEING OVERWRITTEN !!
def optimizer(X: Array[Array[Double]],
              P: Array[Array[Double]],
              Q: Array[Array[Double]],
              num: Array[Array[Double]],
              k: Int = 2,
              max_iter: Int = 1000,
              initial_momentum: Double = 0.5,
              final_momentum: Double = 0.8,
              min_gain: Double = 0.01,
              lr: Double = 500):
DenseMatrix[Double] = {

  assert(P.length == Q.length, "SimilarityScore multi-dim. Arrays must have the same number of rows.")
  assert(P(0).length == Q(0).length, "SimilarityScore multi-dim. Arrays must have the same number of columns.")
  assert(P.length == P(0).length && Q.length == Q(0).length, "SimilarityScore multi-dim. Arrays must be symmetric.")

  // initialize variables
  val n = P.length
  val dCdY = DenseMatrix.zeros[Double](n, k)
  val iY = DenseMatrix.zeros[Double](n, k)
  val gains = DenseMatrix.ones[Double](n, k)
  val Ymat = DenseMatrix.zeros[Double](n, k)

  val PQmat = DenseMatrix((P - Q).map(row => DenseVector(row)): _*)
  val nummat = DenseMatrix(num.map(row => DenseVector(row)): _*)

  for (iter <- 0 until max_iter) {

    // compute SimilarityScores in low dim:
    val Y: Array[Array[Double]] = computeSimilarityScoresT(distances = calculatePairwiseDistances(pca(X, k = k)))._1
    val Ymat: DenseMatrix[Double] = DenseMatrix(Y.map(row => DenseVector(row)): _*)

    // compute gradient: insert into every row of dCdy 4*sum_j(p_ij - q_ij)(y_i - y_j) * (1 + L2)^-1
    // see equation (5) in the original paper: https://jmlr.org/papers/volume9/vandermaaten08a/vandermaaten08a.pdf
    // y points are points in the low-dim space that are moved into clusters by the optimization.
    for (i <- 0 until n) {
      dCdY(i, ::) := sum(tile(PQmat(::, i) *:* nummat(::, i), 1, k) *:* (stackVector(Ymat(i, ::).t, n) - Ymat), Axis._0)
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


// testing optimizer function
/*
val Ptest = computeSimilarityScoresGauss(distances = calculatePairwiseDistances(MNISTdata), sigma = 1)
println(Ptest.length, Ptest(0).length)
val Qtest = computeSimilarityScoresT(distances = calculatePairwiseDistances(MNISTdata))._1
val PQtest = Ptest-Qtest
val Ytest = pcaMNISTdata
val numtest = computeSimilarityScoresT(distances = calculatePairwiseDistances(MNISTdata))._2
val ntest = Qtest.length
val ndimtest = 2
val dCdYtest = DenseMatrix.zeros[Double](ntest, ndimtest)

val PQtestmat =  DenseMatrix(PQtest.map(row => DenseVector(row)): _*)
val Ytestmat = DenseMatrix(Ytest.map(row => DenseVector(row)): _*)
val numtestmat = DenseMatrix(numtest.map(row => DenseVector(row)): _*)


val testdiff = PQtestmat(::,0) *:* (Ytestmat(::,0) - Ytestmat(::,1))
val testdiff2 = tile(PQtestmat(::, 1) *:* numtestmat(::, 1), 1, ndimtest).t.cols
val dimtestdiff2 = tile(PQtestmat(::, 1) *:* numtestmat(::, 1), 1, ndimtest).t.rows
val testrow = Ytestmat(0,::)
val testdiff3 = stackVector(Ytestmat(0,::).t, ntest) - Ytestmat
val testdiffres = sum(tile(PQtestmat(::, 1) *:* numtestmat(::, 1), 1, ndimtest) *:* (stackVector(Ytestmat(1,::).t, ntest) - Ytestmat), Axis._0)

for (i <- 0 until ntest) {
  dCdYtest(i, ::) := sum(tile(PQtestmat(::, 1) *:* numtestmat(::, 1), 1, ndimtest) *:* (stackVector(Ytestmat(i,::).t, ntest) - Ytestmat), Axis._0)
}

println(dCdYtest)
/*
val testsum = for (j <- 0 until ntest) yield PQtest(j) * (Ytest(1) - Ytest(j))
println(testsum)

val dCdYtest = Array.ofDim[Double](Qtest.length, Qtest(0).length)
dCdYtest.zipWithIndex.map{ case (row, index) => (for (j <- 0 until ntest) yield PQtest(j) * (Ytest(index) - Ytest(j))).sum}

(0 until ntest).foreach { i => dCdYtest(i) = for (j <- 0 until ntest) yield PQtest(j) * (Ytest(i) - Ytest(j)))}
 */
println(dCdYtest)
