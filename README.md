# Distributed and parallel t-SNE implementation using Scala and Apache Spark.
R is used for visualization purposes.


## Notes
- Spark only compatible with certain Java JDKs, 11 is supported and seems to work
- SBT: libraryDependencies need to go into the root.settings() codeblock
- SBT: do NOT use "provided" label, IntelliJ won't run the program otherwise
- Retrieve SBT libraryDependencies from https://mvnrepository.com/artifact/org.apache.spark/spark-core


## Resources
- https://docs.scala-lang.org/scala3/book/introduction.html
- https://www.oreilly.com/content/an-illustrated-introduction-to-the-t-sne-algorithm/
- https://www.youtube.com/watch?v=-8V6bMjThNo&list=PLmtsMNDRU0BxryRX4wiwrTZ661xcp6VPM
- https://www.youtube.com/watch?v=-NleKOVsl28
- https://sparkour.urizone.net/recipes/building-sbt/
- https://www.youtube.com/watch?v=NEaUSP4YerM Stats Quest: basic explanation of t-SNE
- https://github.com/scalanlp/breeze/wiki/Linear-Algebra-Cheat-Sheet#operations Breeze LinAlg CheatSheet
- https://databricks-prod-cloudfront.cloud.databricks.com/public/4027ec902e239c93eaaa8714f173bcfc/8623654525287098/4373605817327958/8746817301327119/latest.html PCA in Spark using MLLib



## Additional TODOs
- Docker
- Unit tests


## GCloud docker
1. gcloud auth list
2. gcloud components update
3. gcloud config set project paralleltsne
4. gcloud services enable containerregistry.googleapis.com
5. docker tag parallel_t-sne:0.1.0-SNAPSHOT gcr.io/paralleltsne/parallel_t-sne:0.1.0-SNAPSHOT
6. docker push gcr.io/paralleltsne/parallel_t-sne:0.1.0-SNAPSHOT
7. gcloud compute ssh --zone "europe-north1-a" "tsne-shredder"  --project "paralleltsne"
8. gcloud builds submit --tag gcr.io/paralleltsne/parallel_t-sne:0.1.0-SNAPSHOT
9. gcloud run deploy --image gcr.io/paralleltsne/parallel_t-sne:0.1.0-SNAPSHOT
10. gcloud container clusters create paralleltsne --num-nodes=2 --machine-type=n1-standard-2 --zone=us-central1-a
11. gcloud container clusters get-credentials paralleltsne --zone=us-central1-a
12. kubectl create deployment paralleltsne --image=gcr.io/paralleltsne/parallel_t-sne:0.1.0-SNAPSHOT
13. kubectl expose deployment paralleltsne --type=LoadBalancer --port 80 --target-port 8080
14. kubectl get service paralleltsne
15. kubectl scale deployment paralleltsne --replicas=2
16. kubectl delete service paralleltsne
17. kubectl delete deployment paralleltsne
18. gcloud container clusters delete paralleltsne --zone=us-central1-a
19. gcloud container images delete gcr.io/paralleltsne/parallel_t-sne:0.1.0-SNAPSHOT --force-delete-tags --quiet
20. gcloud container images list

## Problems encountered for final presentation
- Same names for collections, e.g. `DenseMatrix`, both breeze and Spark MLLib
- Functional programming with RDDs and specialized RDD operations require whole different approach.

   
