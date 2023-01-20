# Problems encountered for final presentation
- `map`: Conversion from one collection type into another very costly at times. Needs to be thought through and optimized (e.g. `RDD.collect()` only once).
- Same names for collections, e.g. `DenseMatrix`, both breeze and Spark MLLib
- Functional programming with RDDs and specialized RDD operations require whole different approach.


# Project News
- https://spark.apache.org/docs/latest/rdd-programming-guide.html

- This is the file in saurfang's repo where he initializes his complete t-SNE function in Spark:
   - spark-tsne/spark-tsne-examples/src/main/scala/com/github/saurfang/spark/tsne/examples/MNIST.scala

- On virtuale, the KMeans example of the project exam is worth looking at for how to run stuff in Scala.
  - This is also the way we have to transform the code I have implemented so far.

- For the Spark implementation, I think it's best to use the `RDD`-based `RowMatrix` data type.
  - The LinAlg for manipulating RowMatrices is provided by Breeze, so that most of our functions should work.
  - However, we need to adapt the functions so that they operate on `RDD`s. Maybe we can paralellize our Array[Array[Double]] to transform it into a `RDD`.
  - https://spark.apache.org/docs/3.3.1/mllib-data-types.html
    - PCA using Sparks MLLib: https://spark.apache.org/docs/3.3.1/mllib-dimensionality-reduction.html#principal-component-analysis-pca
    - Playlist with lots of good Spark explanations: https://www.youtube.com/playlist?list=PLlL9SaZVnVgizWn2Gr_ssHExaQUYik2vp
    - Video where it is shown how to create a Spark Context and a RDD in Spark 2.x: https://www.youtube.com/playlist?list=PLlL9SaZVnVgizWn2Gr_ssHExaQUYik2vp


- I'd say Perplexity calculation won't be part of the "just" Scala implementation, because it involves grid search and is complicated to implement. 
   - For the Spark Implementation, we could use the X2P function from saurfang's t-SNE Spark implementation.
 

   
# parallel_t-SNE
Parallel Barnes-Hut t-SNE implementation using Scala and Apache Spark.

## Build
SBT with .jar compiling to be fed into Google Dataproc.

For Mac follow: https://www.google.com/search?q=scala+spark+set+up+mac+m1+intellij&oq=scala+spark+set+up+mac+m1+intel&aqs=chrome.1.69i57j33i160.13532j0j7&sourceid=chrome&ie=UTF-8#fpstate=ive&vld=cid:93ee5e01,vid:ugFBalvTEcE

## Notes
- Spark only compatible with certain Java JDKs, 11 is supported and seems to work
- SBT: libraryDependencies need to go into the root.settings() codeblock
- SBT: do NOT use "provided" label, IntelliJ won't run the program otherwise
- Retrieve SBT libraryDependencies from https://mvnrepository.com/artifact/org.apache.spark/spark-core


## Resources
- https://github.com/saurfang/spark-tsne
- https://docs.scala-lang.org/scala3/book/introduction.html
- https://www.oreilly.com/content/an-illustrated-introduction-to-the-t-sne-algorithm/
- https://www.youtube.com/watch?v=-8V6bMjThNo&list=PLmtsMNDRU0BxryRX4wiwrTZ661xcp6VPM
- https://www.youtube.com/watch?v=-NleKOVsl28
- https://sparkour.urizone.net/recipes/building-sbt/
- https://www.youtube.com/watch?v=NEaUSP4YerM Stats Quest: basic explanation of t-SNE
- https://github.com/scalanlp/breeze/wiki/Linear-Algebra-Cheat-Sheet#operations Breeze LinAlg CheatSheet
- https://databricks-prod-cloudfront.cloud.databricks.com/public/4027ec902e239c93eaaa8714f173bcfc/8623654525287098/4373605817327958/8746817301327119/latest.html PCA in Spark using MLLib


## TODO
### Two implementations: 
- in basic Scala using multi-dim Arrays of type `Array[Array[Double]]`
- (Barnes-Hut t-SNE in Scala)
- in Spark using `pyspark.mllib.linalg.distributed.RowMatrix`

1. Understand the t-SNE algorithm
   1. Calculation of conditional probabilities and similarity scores in original space using Gaussian distribution.
      1. Calculation of the `perplexity` parameter, related to variance of the Gaussian kernel (sigma), using the entropy of the distribution used for calculating the original space similarity scores.
   2. Calculation of conditional probabilities and similarity scores in lower-dim space using t-distribution.
   3. Implement PCA in Scala to obtain initial 2-dim representation of input data.
   4. Implement GD optimization of the KL-divergence (SGD, Momentum?).
2. Understand how to import the MNIST dataset into Scala and make it usable in the required formats for both implementations.
   1. For Spark implementation: Use of Spark Streaming to deal with possibly large datasets?
3. Run small scale script (e.g. similarity score calculation and PCA) in Google Dataproc Cluster.
4. Implement visualization that takes snapshot after each (n-th, resp.) optimization step and turns snapshot-frames into animation.



## Additional TODOs
- Docker
- Unit tests
