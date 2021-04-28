
name := "kafka-to-jdbc"

version := "0.1"

ThisBuild / organization := "com.github.massmutual.streaming.example"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.12.7"

lazy val myMainClass = Some("com.github.massmutual.poc.main.Demo")

lazy val poc = (project in file("poc"))
  .settings(
    settings,
    libraryDependencies ++= {
      commonDependencies ++ Seq(
        dependencies.akkaActor,
        dependencies.akkaActorTestkitTyped,
        dependencies.akkaActorTyped,
        //        dependencies.akkaStream,
        //        dependencies.akkaStreamKafka,
        //        dependencies.antlrRuntime,
        dependencies.avro,
        //        dependencies.avro4sCore,
        //        dependencies.avro4sJson,
        dependencies.betterFiles,
        dependencies.commonCodec,
        //        dependencies.dataflattener,
        //        dependencies.json2Avro,
        dependencies.json4sNative,
        dependencies.kafkaAvroSerializer,
        dependencies.kafkaContainer,
        //        dependencies.kafkaProtobufSerializer,
        //        dependencies.kafkaStreams,
        //        dependencies.nscalaTime,
        dependencies.postgresSql,
        dependencies.postgresSqlContainer,
        //        dependencies.scalaJsonschema,
        //        dependencies.scalaKafkaClient,
      )
    },
    sourceGenerators in Compile += (avroScalaGenerateSpecific in Compile).taskValue,
    mainClass in assembly := myMainClass,
    mainClass in(Compile, run) := myMainClass
  )

//finally just aggregate all the child modules into root
lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(DockerPlugin)
  .enablePlugins(ClasspathJarPlugin)
  .aggregate(poc)
  .settings(settings)

parallelExecution in Test := false

//catalog of dependencies
lazy val dependencies =
  new {
    val akkaStreamKafkaV = "2.0.5"
    val akkaV = "2.6.9"
    val antlrRuntimeV = "4.7.2"
    val avro4sCoreV = "4.0.0"
    val avroV = "1.10.0"
    val awsJavaSdkS3V = "1.11.385"
    val betterFilesV = "0.10.1"
    val commonCodecV = "1.9"
    val confluentV = "5.5.1"
    val dataflattenerV = "0.1.0"
    val jakartaWsRsApiV = "2.1.4"
    val json2AvroV = "0.2.9"
    val json4sNativeV = "3.2.11"
    val kafkaClientsV = "2.5.0"
    val kafkaContainerV = "1.15.2" //"1.15.0-rc2" "1.14.3"
    val logbackClassicV = "1.2.3"
    val nscalaTimeV = "2.24.0"
    val postgresSqlContainerV = "1.15.2"
    val postgresSqlV = "42.2.19"
    val randomDataGeneratorV = "2.9"
    val scalaJsonschemaV = "0.4.0"
    val scalaKafkaClientV = "2.3.1"
    val scalatestV = "3.0.5"
    val slf4jV = "1.7.25"
    val sprayJsonV = "1.3.5"

    val akkaActor = "com.typesafe.akka" %% "akka-actor" % akkaV
    val akkaActorTestkitTyped = "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaV
    val akkaActorTyped = "com.typesafe.akka" %% "akka-actor-typed" % akkaV
    val akkaStream = "com.typesafe.akka" %% "akka-stream" % akkaV
    val akkaStreamKafka = "com.typesafe.akka" %% "akka-stream-kafka" % akkaStreamKafkaV
    val antlrRuntime = "org.antlr" % "antlr4-runtime" % antlrRuntimeV
    val avro = "org.apache.avro" % "avro" % avroV
    val avro4sCore = "com.sksamuel.avro4s" %% "avro4s-core" % avro4sCoreV excludeAll (ExclusionRule(organization = "org.json4s"))
    val avro4sJson = "com.sksamuel.avro4s" %% "avro4s-json" % avro4sCoreV excludeAll (ExclusionRule(organization = "org.json4s"))
    val awsJavaSdkS3 = "com.amazonaws" % "aws-java-sdk-s3" % awsJavaSdkS3V
    val betterFiles = "io.methvin" %% "directory-watcher-better-files" % betterFilesV
    val commonCodec = "commons-codec" % "commons-codec" % commonCodecV
    val dataflattener = "com.github.deprosun" %% "dataflattener" % dataflattenerV
    val jakartaWsRsApi = "jakarta.ws.rs" % "jakarta.ws.rs-api" % jakartaWsRsApiV
    val json2Avro = "tech.allegro.schema.json2avro" % "converter" % json2AvroV
    val json4sNative = "org.json4s" %% "json4s-native" % json4sNativeV
    val kafkaAvroSerializer = "io.confluent" % "kafka-avro-serializer" % confluentV exclude("javax.ws.rs", "javax.ws.rs-api")
    val kafkaClients = "org.apache.kafka" % "kafka-clients" % kafkaClientsV
    val kafkaContainer = "org.testcontainers" % "kafka" % kafkaContainerV
    val kafkaJsonSerializer = "io.confluent" % "kafka-json-serializer" % confluentV
    val kafkaProtobufSerializer = "io.confluent" % "kafka-protobuf-serializer" % confluentV exclude("javax.ws.rs", "javax.ws.rs-api")
    val kafkaSchemaRegistry = "io.confluent" % "kafka-schema-registry" % confluentV
    val kafkaStreams = "org.apache.kafka" %% "kafka-streams-scala" % kafkaClientsV
    val logbackClassic = "ch.qos.logback" % "logback-classic" % logbackClassicV
    val nscalaTime = "com.github.nscala-time" %% "nscala-time" % nscalaTimeV
    val postgresSql = "org.postgresql" % "postgresql" % postgresSqlV
    val postgresSqlContainer = "org.testcontainers" % "postgresql" % postgresSqlContainerV
    val randomDataGenerator = "com.danielasfregola" %% "random-data-generator" % randomDataGeneratorV
    val scalaJsonschema = "com.github.andyglow" %% "scala-jsonschema" % scalaJsonschemaV
    val scalaKafkaClient = "net.cakesolutions" %% "scala-kafka-client" % scalaKafkaClientV
    val scalatest = "org.scalatest" %% "scalatest" % scalatestV
    val slf4jApi = "org.slf4j" % "slf4j-api" % slf4jV
    val slf4jLog4j12 = "org.slf4j" % "slf4j-log4j12" % slf4jV
    val sprayJson = "io.spray" %% "spray-json" % sprayJsonV
  }

//define your common dependencies
lazy val commonDependencies = Seq(
  dependencies.kafkaClients % Test classifier "test",
  dependencies.kafkaClients,
  dependencies.kafkaContainer % Test,
  dependencies.randomDataGenerator,
  dependencies.scalatest % "test",
  dependencies.scalatest,
  //  dependencies.schemaRegistryContainer % "test",
  dependencies.slf4jApi,
  dependencies.slf4jLog4j12
)

//common settings
lazy val settings = commonSettings

//compiler options
lazy val compilerOptions = Seq(
  "-unchecked",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-language:postfixOps",
  "-deprecation",
  "-encoding",
  "utf8"
)

lazy val commonSettings = Seq(
  scalacOptions ++= compilerOptions,
  resolvers ++= Seq(
    "Local Maven Repository" at "file://" + Path.userHome.absolutePath + "/.m2/repository",
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots"),
    Resolver.bintrayRepo("cakesolutions", "maven"),
    "Artima Maven Repository" at "http://repo.artima.com/releases",
    "confluent" at "https://packages.confluent.io/maven/",
    "jitpack" at "https://jitpack.io"
  )
)

//of course, our common resolvers and other things like assembly
lazy val assemblySettings = Seq(
  assemblyJarName in assembly := name.value + ".jar",
  assemblyMergeStrategy in assembly := {
    case PathList("META-INF", xs@_*) => MergeStrategy.discard
    case "application.conf" => MergeStrategy.concat
    case x =>
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
  }
)

