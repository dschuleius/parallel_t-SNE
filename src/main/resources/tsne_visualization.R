if (!require(tidyverse)) install.packages("tidyverse", repos="https://cloud.r-project.org")
if (!require(ggplot2)) install.packages("ggplot2", repos="https://cloud.r-project.org")
if (!require(animation)) install.packages("animation", repos="https://cloud.r-project.org")
if (!require(gtools)) install.packages("gtools", repos="https://cloud.r-project.org")
if (!require(yaml)) install.packages("yaml", repos="https://cloud.r-project.org")
if (!require(googleCloudStorageR)) install.packages("googleCloudStorageR", repos="https://cloud.r-project.org")
if (!require(gargle)) install.packages("gargle", repos="https://cloud.r-project.org")

library(googleCloudStorageR)
library(tidyverse)
library(ggplot2)
library(animation)
library(gtools)
library(yaml)
library(gargle)

# Load config
config = yaml.load_file("src/main/resources/config.yaml")

# To obtain credentials for your service account:
# 1. In the Google Cloud console, go to Menu menu > IAM & Admin > Service Accounts. Go to Service Accounts.
# 2. Select your service account.
# 3. Click Keys > Add key > Create new key.
# 4. Select JSON, then click Create. ...
# 5. Copy the JSON file in root directory of your project.
# Click Close.

# setup
Sys.setenv("GCS_DEFAULT_BUCKET" = config$shellConfig$gsBucket,
           "GCS_AUTH_FILE" = config$rConfig$gsAuthFile)

## Fetch token. See: https://developers.google.com/identity/protocols/oauth2/scopes
scope <-c("https://www.googleapis.com/auth/cloud-platform")
token <- token_fetch(scopes = scope)

## Pass your token to gcs_auth
gcs_auth(token = token)

## Perform gcs operations as normal
gcs_global_bucket(config$shellConfig$gsBucket)
objects <- gcs_list_objects()
gcs_get_object(paste0("gs://", config$shellConfig$gsBucket, config$labelFileInGs), saveToDisk = "data/labels_downloaded.txt", overwrite = TRUE)

# concatinate string
data_path <- paste0("data/",config$version,"export/exportIter_")

iterations <- config$tSNE$max_iter
sampleSize <- config$main$sampleSize

for (n in 1:iterations) {
  folder_path <- paste0("data/",config$version,"/export/exportIter_", n)
  old_file_path <- paste0(folder_path, "/part-00000")
  if (file.exists(old_file_path)) {
  file.copy(from = old_file_path, to = paste0("data/",config$version,"/vis_data/"))
  new_file_path <- paste0(paste0("data/",config$version,"/vis_data"), "/exportYRDD_", n, ".txt")
  print(new_file_path)
  file.rename(paste0("data/",config$version,"/vis_data/part-00000"), new_file_path)

  }
}

YmatExports <- list.files(paste0("data/",config$version, "/vis_data/"), "exportYRDD", full.names = TRUE)
YmatExports <- mixedsort(YmatExports)
results <- lapply(YmatExports, function(file) { read.csv(file, FALSE) })
labels <- read.csv("data/labels_downloaded.txt", header = FALSE, nrows = sampleSize)
resultsCombined <- list()

for (i in seq_along(results)) { 
  resultsCombined[[i]] <- cbind(labels, results[[i]])
}

for (i in seq_along(results)) {
  names(resultsCombined[[i]]) <- c("label", "x", "y")
}

# determine axis limits
xmax <- c()
ymax <- c()

for (i in seq_along(results)) {
  xmax[i] <- max(abs(resultsCombined[[i]]$x), ifelse(max(xmax) == -Inf, 0, max(xmax)))
  ymax[i] <- max(abs(resultsCombined[[i]]$y), ifelse(max(ymax) == -Inf, 0, max(ymax)))
}

# plot the points in the xy plane, color according to class
showPlot <- function(pl) {
  ggplot(data = resultsCombined[[pl]], aes(x, y, color = as.factor(label))) +
    geom_point() +
    scale_color_discrete(name = "Class") +
    xlim(-xmax[pl], xmax[pl]) +
    ylim(-ymax[pl], ymax[pl]) +
    labs(title = "t-SNE performed on first 1000 rows of MNIST")

}

for (i in seq_along(results)) {
  print(showPlot(i))
}
