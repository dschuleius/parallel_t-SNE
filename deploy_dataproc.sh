#!/bin/zsh


# Use yq to get values from config.yaml
getYamlValue() {
  yq ".shellConfig.$1" src/main/resources/config.yaml
}

# Package the application
sbt clean package

# Specify core/project
gcloud config set core/project \
  "$(getYamlValue gcProjectName)"

# Copy the jar to the server
#gsutil cp target/scala-2.12/parallel_t-SNE-assembly-0.1.0-SNAPSHOT.jar gs://scala-and-spark/parallel_t-SNE-assembly-0.1.0-SNAPSHOT.jar
gsutil cp \
 target/scala-2.12/parallel_t-sne_2.12-"$(yq ".version" src/main/resources/config.yaml)".jar \
 gs://"$(getYamlValue gsBucket)"/parallel_t-sne_2.12-"$(yq ".version" src/main/resources/config.yaml)".jar

# if shellConfig.createCluster is true, create a new cluster
if [ "$(getYamlValue createCluster)" = "true" ]; then
  # Create the cluster
  gcloud dataproc clusters create "$(getYamlValue clusterName)" \
    --region=us-central1 \
    --zone= \
    --master-machine-type="$(getYamlValue masterMachineType)" \
    --master-boot-disk-size=100 \
    --num-workers=2 \
    --worker-machine-type="$(getYamlValue workerMachineType)" \
    --worker-boot-disk-size=100 \
    --image-version="$(getYamlValue imageVersion)" \
    --bucket="$(getYamlValue gsBucket)"
else # else, start the existing cluster
  # Start the cluster
  gcloud dataproc clusters start "$(getYamlValue clusterName)" \
    --region=us-central1
fi

# Run the job
gcloud dataproc jobs submit spark \
  --cluster="$(getYamlValue clusterName)" \
  --region=us-central1 \
  --class=Main \
  --jars=gs://scala-and-spark/parallel_t-sne_2.12-"$(yq ".version" src/main/resources/config.yaml)".jar
  #  --jars=gs://scala-and-spark/parallel_t-SNE-assembly-0.1.0-SNAPSHOT.jar \

# If shellConfig.deleteCluster is false, stop the cluster
if [ "$(getYamlValue deleteCluster)" = "false" ]; then
  # Stop the cluster
  gcloud dataproc clusters stop "$(getYamlValue clusterName)" \
    --region=us-central1
else # else, delete the cluster
  # Delete cluster
  gcloud dataproc clusters delete "$(getYamlValue clusterName)" \
    --region=us-central1

fi

# Copy Dataproc Job output to local project folder data
gsutil cp -r "gs://$(getYamlValue gsBucket)/export/" "data/$(getYamlValue version)"

# Copy config.yaml to local project folder data
cp src/main/resources/config.yaml "data/$(getYamlValue version)"

# If shellConfig.empyBucket is true, empty the bucket
if [ "$(getYamlValue emptyGSBucket)" = "true" ]; then
  # Empty gs bucket
  gsutil rm -r "gs://$(getYamlValue gsBucket)/export/"
fi

# Run R file to visualize t-SNE
# make sure to set iterations and sampleSize correctly in the R file before running it!
# Rscript src/main/resources/tsne_visualization.R

# To obtain the GIF animation, open the tsne_visualization.R file in RStudio and run the saveGIF(...) command again

# In R file specify output location for exported .png!

