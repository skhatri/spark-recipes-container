## Spark Recipes Container
The purpose of this project is to allow dynamic execution of simple spark tasks without having to build and maintain them in an artifact repository.

For instance, you could do a volume mapping or load your recipe as configmap in Kubernetes and use this image as a generic runner for Spark tasks.

### Export Jars to examples
```
gradle export
```

### Compile recipe code
```
./compiler.sh compile
```

### Run recipe
```
./compiler.sh run demo.Hello
```

### Build Image
```
./container.sh
```

### Run Demo App
```
docker run --rm -it spark-container
```

### Run Custom App
```
mkdir -p tmp/recipe

cat > tmp/recipe/Recipe1.scala <<EOF
package recipe

import org.apache.spark.sql.SparkSession

object Recipe1 extends App {
  val spark = SparkSession.builder().appName("hello-app").master("local[1]").getOrCreate()
  spark.range(10).show(10, false)
  spark.close()
}
EOF

docker run -v `pwd`/tmp:/opt/app/source --rm -it spark-container recipe.Recipe1
```

