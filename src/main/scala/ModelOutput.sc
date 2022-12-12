import breeze.linalg._
import breeze.numerics._

def tsne(data: DenseMatrix[Double], initialSolution: DenseMatrix[Double], perplexity: Double, maxIter: Int): DenseMatrix[Double] = {
  val n = data.rows
  val m = data.cols
  val p = initialSolution.rows
  val q = initialSolution.cols

  // Compute pairwise distances between data points
  val distances = DenseMatrix.tabulate(n, n) { (i, j) =>
    val d = data(i, ::) - data(j, ::)
    dot(d, d)
  }

  // Compute conditional probabilities
  val pConditional = DenseMatrix.zeros[Double](n, n)
  for (i <- 0 until n) {
    val probabilities = DenseVector.zeros[Double](n)
    val distancesFromI = distances(i, ::).inner
    val sigmas = 1.0 / sqrt(distancesFromI)
    val denom = sum(exp(-distancesFromI * sigmas))
    for (j <- 0 until n) {
      probabilities(j) = exp(-distancesFromI(j) * sigmas(j)) / denom
    }
    pConditional(i, ::) := probabilities
  }

  // Compute target probabilities
  val pTarget = DenseMatrix.zeros[Double](n, n)
  val perplexityRoot = perplexity.sqrt
  for (i <- 0 until n) {
    val probabilities = DenseVector.zeros[Double](n)
    val distancesFromI = distances(i, ::).inner
    val sigmas = 1.0 / sqrt(distancesFromI)
    val denom = sum(exp(-distancesFromI * sigmas * perplexityRoot))
    for (j <- 0 until n) {
      probabilities(j) = exp(-distancesFromI(j) * sigmas(j) * perplexityRoot) / denom
    }
    pTarget(i, ::) := probabilities
  }

  // Use gradient descent to optimize solution
  var solution = initialSolution
  var iteration = 0
  while (iteration < maxIter) {
    val qSums = DenseVector.zeros[Double](q)
    for (i <- 0 until p) {
      qSums :+= solution(i, ::)
    }

    val qMeans = qSums / p
    val qTerm = DenseMatrix.zeros[Double](p, q)
    for (i <- 0 until p) {
      qTerm(i, ::) := solution(i, ::) - qMeans
    }

    val qDot = (qTerm.t * qTerm) / p

    val distancesFromSolution = DenseMatrix.zeros[Double](p, p)
    for (i <- 0 until p) {
      for (j <- 0 until p) {
        val d = solution(i, ::) - solution(j, ::)
        distancesFromSolution(i, j) = dot(d, d)
      }
    }

    val pGradients = Dense
