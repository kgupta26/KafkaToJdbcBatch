package com.github.massmutual.poc

import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

import java.util.logging.{Level, LogManager}

trait PostgresDatabase {

  // Postgres JDBC driver uses JUL; disable it to avoid annoying, irrelevant, stderr logs during connection testing
  LogManager.getLogManager.getLogger("").setLevel(Level.OFF);

  def startPostgresDB(): PostgreSQLContainer[_] = {
    val container: PostgreSQLContainer[Nothing] = new PostgreSQLContainer[Nothing](DockerImageName.parse("postgres:9.6.12"))
    container.withInitScript("KafkaBatchDemo/postgresInit.sql")
    container.start()
    container
  }
}
