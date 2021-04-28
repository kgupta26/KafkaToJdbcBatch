package com.github.massmutual.poc

import com.github.massmutual.poc.streamingcomponents.{ConfluentConnect, ConfluentControlCenter, ConfluentSchemaRegistry}
import org.apache.kafka.clients.admin.{AdminClient, AdminClientConfig, NewTopic}
import org.slf4j.Logger
import org.testcontainers.containers.{KafkaContainer, Network}
import org.testcontainers.utility.DockerImageName

import java.awt.Desktop
import java.io.{File, PrintWriter}
import java.net.URI
import java.util.Properties
import scala.util.{Failure, Success, Try}
import scala.collection.JavaConverters._

/**
 * This class is intended to be used when creating an example that uses Kafka and Schema Registry
 * I might add RestProxy and other services if I can find one in the community
 */
trait StreamingPlatform {
  val confluentVersion = "5.5.1"
  var kafka: KafkaContainer = _
  var schemaRegistry: ConfluentSchemaRegistry = _
  var confluentConnect: ConfluentConnect = _
  var controlCenter: ConfluentControlCenter = _
  val network: Network = Network.newNetwork()

  val logger: Logger

  //the implementer explains the example
  def example(): Unit

  //then runExample is called
  //runExample first starts
  //kafka and schema registry containers
  //runs your explained exampled
  //stops all the containers
  def runExample(): Unit = {
    beforeAll()

    val error = Try(example()) match {
      case Failure(exception) => logger.error("Example Failed.")
        Option(exception)
      case Success(_) => // all good
        None
    }

    error foreach { ex =>
      afterAll()
      throw ex
    }

    afterAll()
  }

  private def filmGeneratorScript(confluentConnect: ConfluentConnect, schemaRegistry: ConfluentSchemaRegistry): String =
    s"""
       |#!/bin/bash
       |
       |curl --location --request PUT '${confluentConnect.getConfluentConnectUrl}/connectors/film-generator/config' \\
       |--header 'Content-Type: application/json' \\
       |--data-raw '{
       |  "schema.filename" : "/schemas/films.avsc",
       |  "name": "film-generator",
       |  "connector.class": "io.confluent.kafka.connect.datagen.DatagenConnector",
       |  "tasks.max": "1",
       |  "key.converter": "org.apache.kafka.connect.storage.StringConverter",
       |  "value.converter": "io.confluent.connect.avro.AvroConverter",
       |  "value.converter.schema.registry.url": "http://${schemaRegistry.getNetworkAliases.get(0)}:8081",
       |  "kafka.topic": "films",
       |  "max.interval": "500",
       |  "iterations": "1000000"
       |}'
       |""".stripMargin

  private def generateSampleConnectConfigs(confluentConnect: ConfluentConnect, schemaRegistry: ConfluentSchemaRegistry): Unit = {
    val config = filmGeneratorScript(confluentConnect, schemaRegistry)
    val pw = new PrintWriter(new File("target/film-generator.sh"))
    pw.write(config)
    pw.close()
  }

  private def createFilmTopic(kafka: KafkaContainer) = {
    val configs = new Properties()

    configs.setProperty(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers)

    val admin = AdminClient.create(configs)

    val filmTopic = new NewTopic("films", 2, 1.toShort) :: Nil

    admin.createTopics(filmTopic.asJava).all().get()
  }

  def beforeAll(): Unit = {
    logger.info("Starting all containers...")

    //kafka
    val kafkaImage = DockerImageName.parse(s"confluentinc/cp-enterprise-kafka:$confluentVersion")

    kafka = new KafkaContainer(
      kafkaImage.asCompatibleSubstituteFor("confluentinc/cp-kafka")
    )

    kafka.withNetwork(network)
    kafka.withEnv("KAFKA_METRIC_REPORTERS", "io.confluent.metrics.reporter.ConfluentMetricsReporter")
    kafka.withEnv("CONFLUENT_METRICS_REPORTER_BOOTSTRAP_SERVERS", kafka.getNetworkAliases.get(0) + ":9092")
    //    kafka.withEnv("KAFKA_CONFLUENT_SCHEMA_REGISTRY_URL", kafka.getNetworkAliases.get(0) + ":8081")
    kafka.start()

    System.setProperty("APP_KAFKA_BOOTSTRAP_SERVERS", kafka.getBootstrapServers)

    //create film topic
    createFilmTopic(kafka)

    //schema registry
    schemaRegistry = streamingcomponents.ConfluentSchemaRegistry(confluentVersion, kafka, logger)
    schemaRegistry.start()
    System.setProperty("APP_SCHEMA_REGISTRY_URL", schemaRegistry.getSchemaRegistryUrl)

    //confluent connect
    confluentConnect = ConfluentConnect(confluentVersion, kafka, schemaRegistry, logger)
    confluentConnect.start()

    //generate sample configs
    generateSampleConnectConfigs(confluentConnect, schemaRegistry)

    //control center
    controlCenter = streamingcomponents.ConfluentControlCenter(confluentVersion, kafka, schemaRegistry, confluentConnect, logger)
    controlCenter.start()

    logger.info("All containers started.")
    Desktop.getDesktop.browse(new URI(controlCenter.getControlCenterUrl))
  }

  def afterAll(): Unit = {
    controlCenter.stop()
    schemaRegistry.stop()
    kafka.stop()
  }

}
