package com.github.massmutual.poc.streamingcomponents

import com.github.massmutual.poc.streamingcomponents.ConfluentConnect.imageWithSchemas
import org.slf4j.Logger
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.containers.{GenericContainer, KafkaContainer}
import org.testcontainers.images.builder.ImageFromDockerfile

import java.io.File
import java.nio.file.Paths
import java.time.Duration

object ConfluentConnect {

  val containerName = "cnfldemos/cp-server-connect-datagen:0.3.2-5.5.0"

  private val avroSchemas = Paths.get("poc/src/main/avro")

  private val schemas = new File(avroSchemas.toString).listFiles()

  // we are creating an image from cnfldemos/cp-server-connect-datagen:0.3.2-5.5.0
  // then we add all thr avsc files from src/main/avro directory
  private val baseImage: ImageFromDockerfile = new ImageFromDockerfile(containerName)
    .withDockerfileFromBuilder(builder => {
      val base = builder.from(containerName)

      val withSchemaFiles = schemas.foldLeft(base) { (acc, s) =>
        acc.copy(s.getName, "schemas/" + s.getName)
      }

      withSchemaFiles.build()
    })


  val imageWithSchemas: ImageFromDockerfile = schemas.foldLeft(baseImage) { (acc, s) =>
    acc.withFileFromPath(s.getName, Paths.get(s.getPath))
  }
}

case class ConfluentConnect(confluentVersion: String,
                            kafka: KafkaContainer,
                            schemaRegistry: ConfluentSchemaRegistry,
                            override val logger: Logger,
                            port: Int = 8083)
  extends GenericContainer(imageWithSchemas) {

  this.dependsOn(kafka)
  this.withNetwork(kafka.getNetwork)
  this.withEnv("CONNECT_REST_ADVERTISED_HOST_NAME", "connect") // do we need to add multiple listeners here for external client (i.e. IntelliJ?)
  this.withEnv("CONNECT_GROUP_ID", "compose-connect-group")
  this.withEnv("CONNECT_BOOTSTRAP_SERVERS", kafka.getNetworkAliases.get(0) + ":9092")
  this.withEnv("CONNECT_REST_PORT", s"$port")
  this.withEnv("CONNECT_OFFSET_FLUSH_INTERVAL_MS", "10000")
  this.withEnv("CONNECT_OFFSET_STORAGE_TOPIC", "connect-cluster-offsets")
  this.withEnv("CONNECT_OFFSET_STORAGE_REPLICATION_FACTOR", "1")
  this.withEnv("CONNECT_CONFIG_STORAGE_TOPIC", "connect-cluster-configs")
  this.withEnv("CONNECT_STATUS_STORAGE_TOPIC", "connect-cluster-status")
  this.withEnv("CONNECT_CONFIG_STORAGE_REPLICATION_FACTOR", "1")
  this.withEnv("CONNECT_PRODUCER_INTERCEPTOR_CLASSES", "io.confluent.monitoring.clients.interceptor.MonitoringProducerInterceptor")
  this.withEnv("CONNECT_CONSUMER_INTERCEPTOR_CLASSES", "io.confluent.monitoring.clients.interceptor.MonitoringConsumerInterceptor")
  this.withEnv("CONNECT_LOG4J_LOGGERS", "org.apache.zookeeper=ERROR,org.I0Itec.zkclient=ERROR,org.reflections=ERROR")
  this.withEnv("CONNECT_STATUS_STORAGE_REPLICATION_FACTOR", "1")
  this.withEnv("CONNECT_KEY_CONVERTER", "org.apache.kafka.connect.storage.StringConverter")
  this.withEnv("CONNECT_VALUE_CONVERTER", "io.confluent.connect.avro.AvroConverter")
  this.withEnv("CONNECT_VALUE_CONVERTER_SCHEMA_REGISTRY_URL", "http://" + schemaRegistry.getNetworkAliases.get(0) + ":8081")
  this.withEnv("CONNECT_KEY_CONVERTER_SCHEMA_REGISTRY_URL", "http://" + schemaRegistry.getNetworkAliases.get(0) + ":8081")
  this.withEnv("CONNECT_INTERNAL_KEY_CONVERTER", "org.apache.kafka.connect.json.JsonConverter")
  this.withEnv("CONNECT_INTERNAL_VALUE_CONVERTER", "org.apache.kafka.connect.json.JsonConverter")
  this.withEnv("CONNECT_ZOOKEEPER_CONNECT", kafka.withEmbeddedZookeeper().getNetworkAliases.get(0) + ":2181")
  this.withEnv("CLASSPATH", "/usr/share/java/monitoring-interceptors/monitoring-interceptors-5.5.1.jar")
  //  this.withClasspathResourceMapping("connect-plugins",
  //    "/connect-plugins",
  //    BindMode.READ_ONLY)
  this.withEnv("CONNECT_PLUGIN_PATH", "/usr/share/java,/usr/share/confluent-hub-components")
  this.withExposedPorts(port)
  this.withLogConsumer(new Slf4jLogConsumer(logger))

  //      .withReadTimeout(Duration.ofSeconds(120))
  this.waitingFor(
    Wait.forHttp("/connectors")
      .forStatusCode(200)
      .withStartupTimeout(Duration.ofSeconds(120))
  )

  def getConfluentConnectUrl: String = {
    "http://" + getContainerIpAddress + ":" + getMappedPort(port)
  }

}