//sources ~= Compile ~= (_.filter(_name == "Particular.scala"))

// Define ScalaImplementation as main class
mainClass in (Compile, run) := Some("Main")

// breeze is disabled because spark-mllib also imports breeze but a different version
// so that the program throws an error at compile time.
lazy val root = (project in file("."))
  .settings(
      inThisBuild(List(
//          organization := "com.parallel_t-SNE",
          scalaVersion := "2.12.14",
//          scalaVersion := "2.11.12",
          version := "0.1.0-SNAPSHOT"
      )),
    name := "parallel_t-SNE",
    libraryDependencies += "org.apache.spark" %% "spark-core" % "3.3.1",
    libraryDependencies += "org.apache.spark" %% "spark-sql" % "3.3.1" ,
    libraryDependencies += "org.apache.spark" %% "spark-hive" % "3.3.1",
//     libraryDependencies += "org.scalanlp" %% "breeze" % "2.1.0",
    libraryDependencies += "org.apache.spark" %% "spark-mllib" % "3.3.1",
    libraryDependencies += "com.sandinh" %% "scala-collection-compat-lazyzip" % "2.13.1"
  )

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}


//enablePlugins(JavaAppPackaging)
//enablePlugins(DockerPlugin)
//enablePlugins(AshScriptPlugin)
//
//
//dockerBaseImage := "openjdk:jre-alpine"