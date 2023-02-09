# Distributed and parallel t-SNE implementation using Scala and Apache Spark.
R is used for visualization purposes.

## Structure
We use a central YAML file, `src/main/resources/config.yaml`, that allows setting all parameters for the t-SNE algorithm as well as the Spark configuration and the file path of the input data in one place.

In the `src/main/resources` folder you will also find the the first 2500 rows of the MNIST train set, `MNIST_2500_X.txt`.
For deployment, there is a `deploy_dataproc.sh` script that packages the project, copies it into a Bucket on Google Cloud Storage, creates a Google Dataproc cluster on the Google Cloud Platform, submits the t-SNE Spark job, returns the result, shuts down the cluster and deletes it.

## Description of the parameters


## Deployment

Before running the deployment script, please make sure your system complies with the following:
1. You have a working installation of [SBT](https://www.scala-sbt.org/).
2. You have a working installation of [gcloud CLI](https://cloud.google.com/sdk/docs/install) that is set up and connected to your Google Cloud Platform account.
3. Your system's Java JDK (used for SBT `package`) is compatible with the version of Apache Spark you are using. In case you have a mac, find out [here](https://stackoverflow.com/questions/21964709/how-to-set-or-change-the-default-java-jdk-version-on-macos) how to change your system's default Java JDK.
4. You have created a Bucket for saving the packaged t-SNE Spark job, the input data and the result.

There are some lines of code in the `deploy_dataproc.sh` script that need to be adapted before deployment:

1. As every Bucket (Google Cloud Storage) has a unique ID, make sure to specify your Bucket.
2. In the command for creating the cluster, you can specify your location preferences.

For deployment on a Google Dataproc Cluster, run the `deploy_dataproc.sh` script.



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

## Credits
We relied heavily on the Python implementation from the t-SNE Paper by van der Maaten & Hinton (2008).

   
