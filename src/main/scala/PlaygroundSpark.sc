
import breeze.numerics.{pow, sqrt}
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.linalg.distributed.RowMatrix
import org.apache.spark.sql.SparkSession


val spark = SparkSession.builder()
  .master("local[1]")
  .appName("parallel_t-SNE")
  .config("spark.driver.host", "127.0.0.1")
  .config("spark.driver.bindAddress", "127.0.0.1")
  .getOrCreate()

def distance(xs: List[Double], ys: List[Double]): Double  = {
  sqrt((xs zip ys).map { case (x,y) => pow(y - x, 2) }.sum)
}

val v0 = Vectors.dense(5.0, 4.8, 7.5, 10.0)
val v1 = Vectors.dense(3.2, 2.1, 4.3, 2.8)
val v2 = Vectors.dense(2.2, 1.1, 1.3, 2.3)

val rows = spark.sparkContext.parallelize(Seq(v0, v1, v2))

val pointsmat:RowMatrix = new RowMatrix(rows)
println(pointsmat.numRows())


val pairs  = pointsmat.rows.zipWithIndex().cartesian(pointsmat.rows.zipWithIndex()).flatMap {
  case ((u, i), (v, j)) =>
    if(i < j) {
      val dist = 5
      Seq((i, (j, dist)), (j, (i, dist)))
    } else Seq.empty
}
















