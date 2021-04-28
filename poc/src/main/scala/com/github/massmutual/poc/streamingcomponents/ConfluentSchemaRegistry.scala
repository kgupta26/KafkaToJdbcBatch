package com.github.massmutual.poc.streamingcomponents

import org.slf4j.Logger
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.containers.{GenericContainer, KafkaContainer}
import org.testcontainers.utility.DockerImageName

case class ConfluentSchemaRegistry(confluentVersion: String,
                                   kafka: KafkaContainer,
                                   override val logger: Logger,
                                   port: Int = 8081)
  extends GenericContainer(DockerImageName.parse(s"confluentinc/cp-schema-registry:$confluentVersion")) {
  this.dependsOn(kafka)
  this.withNetwork(kafka.getNetwork)
  this.withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
  this.withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", kafka.getNetworkAliases.get(0) + ":9092")
  this.withEnv("SCHEMA_REGISTRY_LISTENERS", s"http://0.0.0.0:$port")
  this.withExposedPorts(port)
  this.withLogConsumer(new Slf4jLogConsumer(logger))

  //wait sr to finish starting up
  this.waitingFor(Wait.forHttp("/subjects")
    .forStatusCode(200))

  /**
   * @return Schema Registry URL
   */
  def getSchemaRegistryUrl: String = {
    "http://" + getContainerIpAddress + ":" + getMappedPort(port)
  }
}
