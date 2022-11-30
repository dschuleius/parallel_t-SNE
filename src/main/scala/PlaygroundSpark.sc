
import org.apache.spark.mllib.linalg._
import org.apache.spark.rdd.RDD
import org.apache.spark.mllib.linalg.distributed.RowMatrix
import org.apache.spark.sql.SparkSession


val spark = SparkSession.builder()
  .master("local[1]")
  .appName("parallel_t-SNE")
  .config("spark.driver.host", "127.0.0.1")
  .config("spark.driver.bindAddress", "127.0.0.1")
  .getOrCreate();

val df = spark.createDataFrame(
  List(("Scala", 25000), ("Spark", 35000), ("PHP", 21000)))
df.show()

val input = new RowMatrix(
  spark.sparkContext.parallelize(Seq(1 to 3, 4 to 6, 7 to 9, 10 to 12))
    .map(x => Vectors.dense(x.map(_.toDouble).toArray))
)

val colm = input.rows.collect()
colm.foreach(v => println(v))








