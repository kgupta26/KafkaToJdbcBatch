package com.github.massmutual.poc.main

import akka.actor.typed.ActorSystem
import com.github.massmutual.poc.{PostgresDatabase, StreamingPlatform}
import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.{Logger, LoggerFactory}
import org.testcontainers.containers.PostgreSQLContainer

import java.util.Properties
import scala.collection.JavaConverters._

object Demo extends StreamingPlatform with PostgresDatabase {

  override val logger: Logger = LoggerFactory.getLogger(this.getClass)

  case class DatabaseInfo(databaseDriver: String, databaseName: String, connectionUrl: String, databaseUser: String, password: String)

  case class ApplicationSetting(consumerCount: Int,
                                dbInfo: DatabaseInfo,
                                consumerProperties: Properties,
                                topic: String,
                                table: String,
                                historySize: Int,
                                maxRowCount: Int)

  val postgresDatabase: PostgreSQLContainer[_] = startPostgresDB()

  override def example(): Unit = {

    val config: Config = ConfigFactory.load()

    val consumerConfigList = config.getConfigList("consumer.configs").asScala

    val consumerProperties = new Properties

    consumerConfigList.foldLeft(consumerProperties) { (acc, x) =>
      acc.setProperty(x.getString("name"), x.getString("value"))
      acc
    }

    //database info
    val dbInfo = DatabaseInfo(postgresDatabase.getDriverClassName,
      postgresDatabase.getDatabaseName, postgresDatabase.getJdbcUrl,
      postgresDatabase.getUsername, postgresDatabase.getPassword)

    val setting = ApplicationSetting(
      consumerCount = 0,
      dbInfo = dbInfo,
      consumerProperties = consumerProperties,
      topic = "films",
      table = "films",
      historySize = 5,
      maxRowCount = 100)

    //create the consumer actor
    val system: ActorSystem[ProcessOrchestrator.Orchestrate] =
      ActorSystem(ProcessOrchestrator(setting), "TopicToPostgres")

    //start!
    system ! ProcessOrchestrator.Start

  }

  def main(args: Array[String]): Unit = {
    beforeAll()
    example()
  }
}
