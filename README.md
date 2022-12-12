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
- 


## TODO
- find out all about breeze.linalg and breeze.numerics


## Additional
- Docker
- Unit tests
