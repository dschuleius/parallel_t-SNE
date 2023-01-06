# parallel_t-SNE
Parallel Barnes-Hut t-SNE implementation using Scala and Apache Spark.

## Build
SBT with .jar compiling to be fed into Google Dataproc.

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
