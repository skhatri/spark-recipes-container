package demo

import org.apache.spark.sql.SparkSession

object Hello extends App {
  val spark = SparkSession.builder().appName("hello-app").master("local[1]").getOrCreate()
  spark.range(10).show(5, false)
  spark.close()
}
