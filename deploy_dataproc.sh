#!/bin/zsh

# Package the application with assembly
#sbt assembly
sbt package

# Specify core/project
gcloud config set core/project scalablecloudprogdemo

# Copy the jar to the server
# Bucket names are unique --> specify correct bucket!

#gsutil cp target/scala-2.12/parallel_t-SNE-assembly-0.1.0-SNAPSHOT.jar gs://scala-and-spark/parallel_t-SNE-assembly-0.1.0-SNAPSHOT.jar
gsutil cp target/scala-2.12/parallel_t-sne_2.12-0.1.0-SNAPSHOT.jar gs://scala-and-spark-2/parallel_t-sne_2.12-0.1.0-SNAPSHOT.jar

## Create the cluster
gcloud dataproc clusters create t-sne \
  --region=us-central1 \
  --zone=us-central1-f \
  --master-machine-type=n1-standard-4 \
  --master-boot-disk-size=100 \
  --num-workers=2 \
  --worker-machine-type=n1-standard-4 \
  --worker-boot-disk-size=100 \
  --image-version=2.1.2-ubuntu20 \
  --bucket=scala-and-spark-2 \

# Start the cluster
gcloud dataproc clusters start t-sne \
  --region=us-central1

# Run the application
gcloud dataproc jobs submit spark \
  --cluster=t-sne \
  --region=us-central1 \
  --class=Main \
  --jars=gs://scala-and-spark-2/parallel_t-sne_2.12-0.1.0-SNAPSHOT.jar
  #  --jars=gs://scala-and-spark/parallel_t-SNE-assembly-0.1.0-SNAPSHOT.jar \

# Stop the cluster
gcloud dataproc clusters stop t-sne \
  --region=us-central1

# Delete cluster
#gcloud dataproc clusters delete t-sne \
#  --region=us-central1