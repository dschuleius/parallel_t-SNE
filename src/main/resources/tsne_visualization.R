library(tidyverse)
library(ggplot2)
library(animation)
library(gtools)


iterations <- 20 # needs to be changed according to tsne call
sampleSize <- 1000 # needs to be changed according to tsne call

for (n in 1:iterations) {
  folder_path <- paste0("/Users/juli/Documents/WiSe_2223_UniBo/ScalableCloudProg/parralel_t-SNE/data/export/exportIter_", n)
  old_file_path <- paste0(folder_path, "/part-00000")
  if (file.exists(old_file_path)) {
  new_file_path <- paste0(folder_path, "/exportYRDD_", n, ".txt")
  file.rename(old_file_path, new_file_path)
  file.copy(from = new_file_path, to = "/Users/juli/Documents/WiSe_2223_UniBo/ScalableCloudProg/parralel_t-SNE/data/export/")
}}

YmatExports <- list.files("/Users/juli/Documents/WiSe_2223_UniBo/ScalableCloudProg/parralel_t-SNE/data/export/", "exportYRDD", full.names = TRUE)
YmatExports <- mixedsort(YmatExports)
results <- lapply(YmatExports, function(file) { read.csv(file, FALSE) })
labels <- read.csv("/Users/juli/Documents/WiSe_2223_UniBo/ScalableCloudProg/parralel_t-SNE/src/main/resources/mnist2500_labels.txt", header = FALSE, nrows = sampleSize)

resultsCombined <- list()
for (i in seq_along(results)) { 
  resultsCombined[[i]] <- cbind(labels, results[[i]])
}
  
for (i in seq_along(results)) {
  names(resultsCombined[[i]]) <- c("label", "x", "y")
}

# GIF creation

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


# combine plots to animation
makeAnimation <- function(n = length(results), steplength = 1) {
  lapply(seq(1, n, steplength), function(i) {
    print(showPlot(i))
  })
}

# render animation
saveGIF(makeAnimation(steplength = 1), interval = 0.1, movie.name = "tsne_viz.gif", loop = 1)


# plot to .png files

#for (i in seq_along(results)) {
#  png(paste0("/Users/juli/Desktop/tsneviz/tnse_viz_", i, ".png"), width = 1000, height = 1000)
#  print(showPlot(i))
#  dev.off()
#}