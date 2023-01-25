library(dplyr)
library(ggplot2)
library(animation)

YmatExports <- list.files("/Users/juli/Documents/WiSe_2223_UniBo/ScalableCloudProg/parralel_t-SNE/data", "exportYmat_", full.names = TRUE)
results <- lapply(YmatExports, function(file) { read.csv(file, FALSE) })
labels <- read.csv("/Users/juli/Documents/WiSe_2223_UniBo/ScalableCloudProg/parralel_t-SNE/data/mnist2500_labels.txt", header = FALSE, nrows = 10)

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
    xlim(-xmax[pl], xmax[pl]) +
    ylim(-ymax[pl], ymax[pl])

}

# combine plots to animation
makeAnimation <- function(n = length(results), steplength = 1) {
  lapply(seq(1, n, steplength), function(i) {
    print(showPlot(i))
  })
}

# render animation
saveGIF(makeAnimation(steplength = 1), interval = 0.05, movie.name = "tsne_viz.gif", loop = 1)
