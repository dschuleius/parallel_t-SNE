// file where all code for tSNE Scala implementation goes into

import scala.io.Source

object tSNEScala extends App {
  def importData(fileName: String, sampleSize: Int): Array[Array[Double]] = {
    // Read the file and split it into lines
    val lines = Source.fromFile(fileName).getLines.take(sampleSize).toArray

    // Split each line into fields and convert the fields to doubles
    // trim removes leading and trailing blank space from each field
    val data = lines.map(_.trim.split("\\s+").map(_.toDouble))

    // Return the data as an array of arrays of doubles
    data
  }
}
