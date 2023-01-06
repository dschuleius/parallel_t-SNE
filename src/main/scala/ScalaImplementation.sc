// This file contains an implementation of t-SNE using plain Scala without parallel data collections

import scala.io.Source
import breeze.linalg._
import breeze.stats.mean


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
val labels = importData("/Users/juli/Documents/WiSe_2223_UniBo/ScalableCloudProg/parralel_t-SNE/data/mnist2500_labels.txt", 200)
val data = importData("/Users/juli/Documents/WiSe_2223_UniBo/ScalableCloudProg/parralel_t-SNE/data/mnist2500_X.txt", 200)


// ----------------------------
// HELPER FUNCTIONS for L2 distance and Cov-Matrix
def euclideanDistance(point1: Array[Double], point2: Array[Double]): Double = {
  // Calculate the squared Euclidean distance between the two points
  val squaredDistance = point1.zip(point2).map { case (x, y) => (x - y) * (x - y) }.sum

  // Return the Euclidean distance
  Math.sqrt(squaredDistance)
}

// COPIED FROM https://groups.google.com/g/scala-breeze/c/fMsAQvHFkMs
def covMatrixCalc(A:DenseMatrix[Double]):DenseMatrix[Double] = {
  val n = A.cols
  val D:DenseMatrix[Double] = A.copy
  val mu:DenseVector[Double] = sum(D,Axis._1) *:* (1.0/(n-1)) // sum along rows --> col vector
  (0 until n).map(i => D(::,i):-=mu)
  val C = (D*D.t) *:* (1.0/(n-1))
  // make exactly symmetric
  (C+C.t) *:* 0.5

}
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


// testing of calculatePairwiseDistances function
val X = Array(Array(1, 1.5, 1.8), Array(8, 9, 8.2), Array(15, 14, 3.1))
println(euclideanDistance(point1 = Array(1, 1.2, 2), point2 = Array(10, 11, 12)))
println(calculatePairwiseDistances(data)(1)(199))

def computeSimilarityScores(distances: Array[Array[Double]], sigma: Double): Array[Array[Double]] = {
  // check that distance matrix is symmetric, i.e. n x n, otherwise throw error
  assert(distances.length == distances(0).length, "Distance-Matrix is not symmetric.")
  val n = distances.length
  val unnormSimilarities = Array.ofDim[Double](n, n)
  val normSimilarities = Array.ofDim[Double](n, n)
  for (i <- 0 until n) {
    for (j <- 0 until n) {
      // use yield to obtain "buffered for-loop" that returns all collection of all yielded values.
      unnormSimilarities(i)(j) = {
        (math.exp(-1 * scala.math.pow(distances(i)(j), 2) / (2 * scala.math.pow(sigma, 2)))) /
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



// testing of computeSimilarityScore function
println(computeSimilarityScores(distances = calculatePairwiseDistances(data), sigma = 1)(5)(12))



val XDense = DenseMatrix(X: _*)
val Xmean = mean(XDense(*, ::))
println(Xmean)
println(covMatrixCalc(XDense))

// obtain first lower dimensional representation of points using PCA
def pca(data: Array[Array[Double]]): Array[Array[Double]] = {

  // assert non-empty Array and no empty rows
  if (data.isEmpty || data.exists(_.isEmpty)) {
    throw new IllegalArgumentException("Data array cannot be empty or contain empty rows")
  }

  // assert symmetric multi-dim Array
  if (data.map(_.length).distinct.length > 1) {
    throw new IllegalArgumentException("Rows in data array must have the same number of columns")
  }

  // Convert data to Breeze DenseMatrix
  val dataMatrix = DenseMatrix(data.map(row => DenseVector(row)): _*)

  // Subtract mean from each column
  val meanVector = breeze.stats.mean(dataMatrix(::, *))
  val centeredDataMatrix = dataMatrix(::, *) - meanVector.t

  // Compute covariance matrix
  val covMatrix = breeze.linalg.cov(centeredDataMatrix)

  // Compute eigenvalues and eigenvectors of covariance matrix
  // https://lamastex.github.io/scalable-data-science/db/xtraResources/LinearAlgebra/LAlgCheatSheet.html
  //val (eigenValues, eigenVectors) = breeze.linalg.eig(covMatrix)

  // Sort eigenvalues and eigenvectors in descending order
  val sortedEigenVectors = eigenVectors(*, eigenValues.argsort.reverse)

  // Project data onto top k eigenvectors
  val k = 2  // choose top k eigenvectors
  val topEigenVectors = sortedEigenVectors(::, 0 until k)
  val projectedData = (topEigenVectors.t * centeredDataMatrix.t).t

  // Convert projected data back to Array[Array[Double]]
  projectedData.toArray.map(_.toArray)
}
