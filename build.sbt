//sources ~= Compile ~= (_.filter(_name == "Particular.scala"))

// Define ScalaImplementation as main class
mainClass in (Compile, run) := Some("Main")

// Read from config.yaml
import com.typesafe.config.{Config, ConfigFactory}
//val config = ConfigFactory.load("src/main/resources/application.conf")
//val config: Config = ConfigFactory.load()
//val version_nm = config.getString("version")

// breeze is disabled because spark-mllib also imports breeze but a different version
// so that the program throws an error at compile time.
lazy val root = (project in file("."))
  .settings(
      inThisBuild(List(
//          organization := "com.parallel_t-SNE",
          scalaVersion := "2.12.14",
//          scalaVersion := "2.11.12",
          version := "test-run"
      )),
    name := "parallel_t-SNE",
    libraryDependencies += "org.apache.spark" %% "spark-core" % "3.3.1",
//    libraryDependencies += "org.apache.spark" %% "spark-sql" % "3.3.1" ,
//    libraryDependencies += "org.apache.spark" %% "spark-hive" % "3.3.1",
//     libraryDependencies += "org.scalanlp" %% "breeze" % "2.1.0",
    libraryDependencies += "org.apache.spark" %% "spark-mllib" % "3.3.1",
    libraryDependencies += "org.yaml" % "snakeyaml" % "1.33"
//    libraryDependencies += "com.sandinh" %% "scala-collection-compat-lazyzip" % "2.13.1"
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