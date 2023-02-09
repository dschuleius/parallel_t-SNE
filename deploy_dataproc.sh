#!/bin/zsh

# Package the application with assembly
#sbt assembly
sbt clean package

# change default Java version to 11
# export JAVA_HOME=`/usr/libexec/java_home -v 11.0`

# Specify core/project
gcloud config set core/project scalablecloudprogdemo

# Copy the jar to the server
#gsutil cp target/scala-2.12/parallel_t-SNE-assembly-0.1.0-SNAPSHOT.jar gs://scala-and-spark/parallel_t-SNE-assembly-0.1.0-SNAPSHOT.jar
gsutil cp target/scala-2.12/parallel_t-sne_2.12-0.1.0-SNAPSHOT.jar gs://scala-and-spark/parallel_t-sne_2.12-0.1.0-SNAPSHOT.jar

## Create the cluster
#gcloud dataproc clusters create t-sne \
#  --region=us-central1 \
#  --zone=us-central1-f \
#  --master-machine-type=n1-standard-4 \
#  --master-boot-disk-size=100 \
#  --num-workers=2 \
#  --worker-machine-type=n1-standard-4 \
#  --worker-boot-disk-size=100 \
#  --image-version=2.1.2-ubuntu20 \
#  --bucket=scala-and-spark

# Start the cluster
gcloud dataproc clusters start t-sne \
  --region=us-central1

# Run the application
gcloud dataproc jobs submit spark \
  --cluster=t-sne \
  --region=us-central1 \
  --class=Main \
  --jars=gs://scala-and-spark/parallel_t-sne_2.12-0.1.0-SNAPSHOT.jar
  #  --jars=gs://scala-and-spark/parallel_t-SNE-assembly-0.1.0-SNAPSHOT.jar \

# Stop the cluster
gcloud dataproc clusters stop t-sne \
  --region=us-central1

# Delete cluster
gcloud dataproc clusters delete t-sne \
  --region=us-central1

# Copy Dataproc Job output to local project folder data
gsutil cp gs://scala-and-spark/export/*.txt data/

# Run R file to visualize t-SNE
# make sure to set iterations and sampleSize correctly in the R file before running it!
Rscript src/main/resources/tsne_visualization.R

# To obtain the GIF animation, open the tsne_visualization.R file in RStudio and run the saveGIF(...) command again

# In R file specify output location for exported .png!

