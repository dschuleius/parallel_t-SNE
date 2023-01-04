// This file contains an implementation of t-SNE using plain Scala without parallel data collections


import breeze.linalg._
import breeze.stats.mean

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

def calculatePairwiseDistances(points: Array[Array[Double]]): Array[Array[Double]] = {
  // initialize the distance matrix
  val distances = Array.ofDim[Double](points.length, points.length)

  // calculate the pairwise distances between all points
  for (i <- points.indices; j <- points.indices) {
    distances(i)(j) = euclideanDistance(points(i), points(j))
  }

  // Return the distance matrix
  distances
}



// testing of calculatePairwiseDistances function
val X = Array(Array(1, 1.5, 1.8), Array(8, 9, 8.2), Array(15, 14, 3.1))
println(euclideanDistance(point1 = Array(1, 1.2, 2), point2 = Array(10, 11, 12)))
println(calculatePairwiseDistances(X)(0)(0))

def computeSimilarityScores(distances: Array[Array[Double]], sigma: Double): Array[Array[Double]] = {
  val n = distances.length
  val unnormSimilarities = Array.ofDim[Double](n, n)
  val normSimilarities = Array.ofDim[Double](n, n)
  for (i <- 0 until n) {
    for (j <- 0 until n) {
      unnormSimilarities(i)(j) = math.exp(-1 * scala.math.pow(distances(i)(j), 2) / (2 * scala.math.pow(sigma, 2)))
    }
  }
  for (i <- 0 until n) {
    for (j <- 0 until n) {
      normSimilarities(i)(j) = (unnormSimilarities(i)(j) + unnormSimilarities(j)(i)) / (2 * n)
    }
  }
  normSimilarities
}


// testing of computeSimilarityScore function
println(computeSimilarityScores(distances = calculatePairwiseDistances(X), sigma = 1)(1)(0))
val XDense = DenseMatrix(X: _*)
val Xmean = mean(XDense(*, ::))
println(Xmean)
println(covMatrixCalc(XDense))

// obtain first lower dimensional representation of points using PCA

// this is ChatGPT output that does not work as the breeze.linalg.mean function and
// the breeze.linalg.cov functions don't seem to exist (anymore)
/*
def pca(data: Array[Array[Double]]): Array[Array[Double]] = {
  // Calculate the mean of each feature
  val datamatrix = DenseMatrix(data: _*)
  val means = mean(datamatrix(*, ::)).toArray

  // Center the data by subtracting the mean
  //val centeredData = data.map(row => row.zip(means).map { case (x, mean) => x - mean })

  // Calculate the covariance matrix of the centered data
  val covMat = cov(DenseMatrix(data: _*), center = false)

  // Perform singular value decomposition
  val svd.SVD(u, s, v) = svd(covMat)

  // Take the first two columns of U as the basis
  val basis = u(::, 0 to 1).toArray

  // Project the data onto the new basis
  data.map(row => row.zip(basis).map { case (x, b) => DenseVector(b) dot DenseVector(row) })
}
