package com.github.massmutual.poc.streamingcomponents

import org.slf4j.Logger
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.{GenericContainer, KafkaContainer}
import org.testcontainers.utility.DockerImageName

case class ConfluentControlCenter(confluentVersion: String,
                                  kafka: KafkaContainer,
                                  schemaRegistry: ConfluentSchemaRegistry,
                                  confluentConnect: ConfluentConnect,
                                  override val logger: Logger,
                                  port: Int = 9021) extends GenericContainer(DockerImageName.parse(s"confluentinc/cp-enterprise-control-center:$confluentVersion")) {
  this.dependsOn(kafka, schemaRegistry)
  this.withNetwork(kafka.getNetwork)
  this.withEnv("CONTROL_CENTER_BOOTSTRAP_SERVERS", kafka.getNetworkAliases.get(0) + ":9092")
  this.withEnv("CONTROL_CENTER_REPLICATION_FACTOR", "1")
  this.withEnv("CONTROL_CENTER_REST_LISTENERS", s"http://0.0.0.0:$port")
  this.withEnv("CONTROL_CENTER_SCHEMA_REGISTRY_URL", schemaRegistry.getNetworkAliases.get(0) + ":8081")
  this.withEnv("CONTROL_CENTER_CONNECT_CLUSTER", confluentConnect.getNetworkAliases.get(0) + ":8083")
  this.withExposedPorts(port)
  this.withLogConsumer(new Slf4jLogConsumer(logger))

  //wait sr to finish starting up
  //  this.waitingFor(Wait.forHttp("/subjects")
  //    .forStatusCode(200))

  /**
   * @return Schema Registry URL
   */
  def getControlCenterUrl: String = {
    "http://" + getContainerIpAddress + ":" + getMappedPort(port)
  }
}
