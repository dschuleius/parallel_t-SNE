# Distributed and parallel t-SNE implementation using Scala and Apache Spark.
R is used for visualization purposes.

## Structure
We use a central YAML file, `src/main/resources/config.yaml`, that allows setting all parameters for the t-SNE algorithm as well as the Spark configuration and the deployment on GCP.

For deployment, there is a `deploy_dataproc.sh` script that packages the project, copies it into a Bucket on Google Cloud Storage, creates a Google Dataproc cluster on the Google Cloud Platform, submits the t-SNE Spark job, returns the result, shuts down the cluster and deletes it.
You can deploy different versions with different names where the output of the GCP Spark job will be saved into different subdirectories of the local data folder.

## Description of the parameters in `config.yaml`
- `scala-version`: indicate Scala version you are using as string.
- `name`: indicate the desired name of your Spark application as string.
- `version`: indicate a desired name for the version of your Spark application as string, e.g., "test-run".
- `dataFileInGs`: indicate the location of your datafiles (.txt) in your GCP bucket, e.g., "/resources/mnist2500_X.txt".

### ShellConfig
- `gcProjectName`: indicate the unique name of your project on GCP as string.
- `gsBucket`: indicate the unique name of your bucket on GCP as string.
- `clusterName`: indicate your desired name for the cluster as string.
- `createCluster`: specify whether a new cluster should be created as boolean.
- `deleteCluster`: specify whether the cluster should be deleted after the spark job submission as boolean.
- `emptyGSBucket`: specify whether the GCP bucket should be emptied after the spark job submission as boolean.
- `masterMachineType`: indicate the desired GCP Dataproc master machine type, e.g., "n1-standard-4".
- `workerMachineType`: indicate the desired GCP Dataproc worker machine type, e.g., "n1-standard-4".
- `imageVersion`: indicate your desired imageVersion for the Dataproc cluster, e.g., "2.1.2-ubuntu20"

### sparkConfig
- `local`: indicate whether Apache Spark should run in local mode as boolean, e.g., false.
- `appName`: specify the Apache Spark name of the application as string.
- `master`: specify the master of the `sparkConf()`, e.g., "local[*]".
- `shufflePartitions`: speficy the desired `spark.sql.shuffle.partitions` value as string.
- `defaultParallelism`: speficy the desired `spark.default.parallelism` value as string.
- `sparkBindHost`: for Spark local mode, e.g., "127.0.0.1" (localhost).
- `sparkBindAddress`: for Spark local mode, e.g., "127.0.0.1" (localhost).

### main
- `sampleSize`: the sample size as integer.
- `partitions`: the desired number of partitions for the `RangePartitioner` as integer.
- `perplexity`: the desired perplexity as integer. Common values range from 5 to 50.

### tSNE
- `k`: indicate the desired dimension of the embedding produced by t-SNE. Normally 2.
- `max_iter`: specify the desired number of iterations for the GD optimization as integer.
- `initial_momentum`: initial momentum, normally set to 0.5.
- `final_momentum`: final momentum, normally set to 0.8.
- `lr`: specify the desired learning rate for the GD optimization. Normally 500.0.
- `minimumgain`: indicate the desired minimum gain for the adaptive learning rates in the GD optimization. Normally 0.01.
- `export`: boolean. If set to true, the low-dimensional embedding of the input points will be saved in a text file after every iteration.
- `print`: boolean. If set to true, the tSNE function will print intermediate results.
- `takeSamples`: int. If print = true, the first `takeSamples` rows of every intermediate result will be printed to the console.
- `kNNapprox`: boolean. It true, applies a kNN approximation of the distances for computing the high-dimensional similarity scores (P). Results in faster execution.


### PCA
- `reduceTo`: specify the number of dimensions that the t-SNE function starts with as input dimensions. Usually set to 50.

## Deployment

Before running the deployment script, please make sure your system complies with the following:
1. You have a working installation of [SBT](https://www.scala-sbt.org/).
2. You have a working installation of [gcloud CLI](https://cloud.google.com/sdk/docs/install) that is set up and connected to your Google Cloud Platform account.
3. Your system's Java JDK (used for SBT `package`) is compatible with the version of Apache Spark you are using. In case you have a mac, find out [here](https://stackoverflow.com/questions/21964709/how-to-set-or-change-the-default-java-jdk-version-on-macos) how to change your system's default Java JDK.
4. You have created a Bucket for saving the packaged t-SNE Spark job, the input data and the result.

Adjust all parameters in the `config.yaml` file to your liking.

For deployment on a Google Dataproc Cluster, run the `deploy_dataproc.sh` script.

For visualization, the `deploy_dataproc.sh` script automatically runs the `tsne_visualization.R` script, taking into account all parameters you have indicated on the `config.yaml` file. The visualizations produced will be one PDF that contains the visualizations of all optimization steps that are saved to the root directory of your project. Alternatively, you can use the provided R script `tsne_visualization.R` in the `src/main/resources` folder manually (e.g., using RStudio).

## Additional resources on t-SNE
- https://www.youtube.com/watch?v=NEaUSP4YerM Stats Quest: basic explanation of t-SNE
- https://www.youtube.com/watch?v=MnRskV3NY1k


## Credits
We relied on the Python implementation from the [t-SNE Paper by van der Maaten & Hinton (2008)](https://jmlr.org/papers/volume9/vandermaaten08a/vandermaaten08a.pdf).

   
