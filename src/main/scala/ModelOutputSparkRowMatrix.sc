import org.apache.spark.mllib.linalg._
import org.apache.spark.mllib.linalg.distributed.RowMatrix
import org.apache.spark.rdd.RDD

// This implementation assumes that the input data has already been preprocessed
// and is in the form of a RowMatrix with rows representing the datapoints and columns
// representing the features.


// Compute the Gaussian kernel value between two datapoints
def gaussianKernel(x: Vector, y: Vector, sigma: Double): Double = {
  val d = breeze.linalg.squaredDistance(x.toBreeze, y.toBreeze)
  math.exp(-d / (2 * sigma * sigma))
}

// Compute the joint probabilities between all pairs of datapoints
def jointProbabilities(data: RowMatrix, sigma: Double): RDD[(Long, Long, Double)] = {
  val pairwiseSquaredDistances = data.columnSimilarities()
  val pairwiseDistances = pairwiseSquaredDistances.mapValues(math.sqrt)
  val pairwiseKernels = pairwiseDistances.mapValues(d => math.exp(-d / (2 * sigma * sigma)))

  pairwiseKernels.cache()
}

// Compute the low-dimensional embedding of the data using t-SNE
def tsne(data: RowMatrix, perplexity: Double, maxIter: Int): RDD[(Long, Vector)] = {
  val n = data.numRows().toInt
  val d = data.numCols().toInt

  // Compute the joint probabilities between all pairs of datapoints
  val sigma = 1.0
  val pairwiseProbabilities = jointProbabilities(data, sigma)

  // Initialize the low-dimensional representation with random Gaussian noise
  val rand = new scala.util.Random()
  val randData = data.rows.mapPartitionsWithIndex { (index, iter) =>
    Iterator.single((index, Vectors.dense(Array.fill(d)(rand.nextGaussian()))))
  }

  // Perform the optimization steps
  var prevCost = 0.0
  var currCost = 0.0
  for (i <- 0 until maxIter) {
    // Compute the pairwise affinities between the datapoints
    val q = pairwiseProbabilities.join(randData)
      .mapValues { case ((p1, x1), x2) => (p1 * p1, x1 - x2) }
      .reduceByKey { case (a, b) => (a._1 + b._1, a._2 + b._2) }
      .mapValues { case (p, dx) => p / dx.norm(2) }

    // Use the affinities to update the low-dimensional representation
    val grad = randData.join(q).mapValues { case (x1, q) => q * (x1 - q) }
    val updatedData = grad.foldBy
