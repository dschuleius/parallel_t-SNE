# parallel_t-SNE
Parallel Barnes-Hut t-SNE implementation using Scala and Apache Spark.

## Build
SBT with .jar compiling to be fed into Google Dataproc.

## Notes
- Spark only compatible with certain Java JDKs, 11 is supported and seems to work
- SBT: libraryDependencies need to go into the root.settings() codeblock
- SBT: do NOT use "provided" label, IntelliJ won't run the program otherwise
- Retrieve SBT libraryDependencies from https://mvnrepository.com/artifact/org.apache.spark/spark-core


## TODO
- find out all about breeze.linalg and breeze.numerics
