#!/bin/zsh

# Package the application with assembly
sbt assembly

# Copy the jar to the server
gsutil cp target/scala-2.12/parallel_t-SNE-assembly-0.1.0-SNAPSHOT.jar gs://scala-and-spark/parallel_t-SNE-assembly-0.1.0-SNAPSHOT.jar

# Run the application
gcloud dataproc jobs submit spark \
  --cluster=cluster-f683 \
  --region=us-central1 \
  --class=Main \
  --jars=gs://scala-and-spark/parallel_t-SNE-assembly-0.1.0-SNAPSHOT.jar