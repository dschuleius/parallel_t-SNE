ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.10"

lazy val root = (project in file("."))
  .settings(
    name := "parallel_t-SNE",
    libraryDependencies += "org.apache.spark" %% "spark-core" % "3.3.1",
    libraryDependencies += "org.apache.spark" %% "spark-sql" % "3.3.1" ,
    libraryDependencies += "org.apache.spark" %% "spark-hive" % "3.3.1",
    libraryDependencies += "org.scalanlp" %% "breeze" % "2.1.0"
  )
