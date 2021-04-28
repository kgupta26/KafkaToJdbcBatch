package com.github.massmutual.poc.main

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.github.massmutual.poc.main.Demo.DatabaseInfo
import com.massmutual.poc.verticatopostgres.Film
import com.sun.net.httpserver.HttpExchange
import org.apache.kafka.clients.consumer.{Consumer, ConsumerRecords, KafkaConsumer}
import org.apache.kafka.common.TopicPartition
import org.slf4j.{Logger, LoggerFactory}

import java.sql.{Connection, DriverManager}
import java.time.Duration
import java.util.Properties
import scala.collection.JavaConverters._

object KafkaToJdbcSink {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  trait Consume

  case class PollRecords(duration: Duration, replyTo: ActorRef[ProcessOrchestrator.Orchestrate]) extends Consume

  case class Pause(replyTo: ActorRef[ProcessOrchestrator.Orchestrate]) extends Consume

  case class BeginStoreProc(replyTo: ActorRef[ProcessOrchestrator.Orchestrate]) extends Consume

  case class Resume(replyTo: ActorRef[ProcessOrchestrator.Orchestrate]) extends Consume

  case class UpdateConfig(consumerProperties: Properties,
                          replyTo: ActorRef[ProcessOrchestrator.Orchestrate]) extends Consume

  case object Stop extends Consume

  private def consumerRecordsToSqlRows(records: ConsumerRecords[String, Film]): String = {

    import scala.collection.JavaConverters._

    //combine rows in into string representation
    records.iterator().asScala.foldLeft("") { (acc, kafkaMessage) =>
      val id = kafkaMessage.value().id
      val name = kafkaMessage.value().name
      val rowString = s"( '$id', '$name' )"
      if (acc.isEmpty) rowString else acc + "," + rowString

    }
  }

  private def insertJdbc(connection: Connection, table: String,
                         records: ConsumerRecords[String, Film]): Int = {

    val sqlRows = consumerRecordsToSqlRows(records)

    val statement = connection.createStatement()

    val sql =
      s"""
         |INSERT INTO $table (id, name) VALUES $sqlRows
         |""".stripMargin

    val recordCount = statement.executeUpdate(sql)

    statement.close()

    recordCount
  }

  private def pauseConsumer(topic: String, consumer: KafkaConsumer[String, Film], context: ActorContext[Consume], replyTo: ActorRef[ProcessOrchestrator.Orchestrate]) = {
    val currentAssignment = consumer.assignment().asScala map { x =>
      new TopicPartition(topic, x.partition())
    }

    context.log.info(s"Pausing consumer with assigned partition of ${currentAssignment.map(_.partition()).mkString(",")}")

    //pause consumption
    consumer.pause(currentAssignment.asJava)

    replyTo ! ProcessOrchestrator.ConsumerPaused(context.self)
  }

  /**
   * The state of the actor when the consumer is in paused state
   */
  def consumerPaused(connection: Connection,
                     topic: String,
                     table: String,
                     consumer: KafkaConsumer[String, Film]): Behavior[Consume] = Behaviors.setup { context =>

    Behaviors.receiveMessage {

      case Pause(_) =>

        context.log.info(s"${context.self.path} Consumer is ALREADY paused. Not polling more records")

        Behaviors.same

      case PollRecords(_, _) =>

        context.log.info(s"${context.self.path} Consumer is paused. Not polling more records")

        Behaviors.same

      case Stop =>
        //when its paused we know all the polls will not get any data
        //hence we can simply close the connection and no offsets will be committed
        //especially when auto commit is on.
        consumer.close()

        context.log.info(s"Consumer '${context.self.path.name}' stopped.")

        Behaviors.stopped

      case UpdateConfig(newProps, replyTo) =>
        val topics = consumer.subscription()

        consumer.close()

        val newConsumer = new KafkaConsumer[String, Film](newProps)

        newConsumer.subscribe(topics)

        replyTo ! ProcessOrchestrator.UpdatedConfig

        //the consumers are paused right now, so pause it
        pauseConsumer(topic, newConsumer, context, replyTo)

        consumerPaused(connection, topic, table, newConsumer)

      case Resume(replyTo) =>
        context.log.info("Resuming the consumer..")

        val partitions = consumer.assignment().asScala map { x =>
          new TopicPartition(topic, x.partition())
        } asJava

        consumer.resume(partitions)

        //tell the orchestrator
        replyTo ! ProcessOrchestrator.ConsumerResumed

        //back to regular connected state
        kafkaPostgresConnected(connection, topic, table, consumer)

      case BeginStoreProc(replyTo) =>
        context.log.info("performing some stored proc...")

        Thread.sleep(2000)

        replyTo ! ProcessOrchestrator.StoredProcFinished

        Behaviors.same

    }
  }

  def kafkaPostgresConnected(connection: Connection,
                             topic: String,
                             table: String,
                             consumer: KafkaConsumer[String, Film]): Behavior[Consume] = Behaviors.setup { context =>

    Behaviors.receiveMessage {

      case PollRecords(duration, replyTo) =>
        val records = consumer.poll(duration)

        if (records.isEmpty) {
          context.log.info(s"${context.self} No records in topic $topic")
        } else {
          val insertedRows = insertJdbc(connection, table, records)

          context.log.info(s"${context.self} Inserted $insertedRows records into $table")

          //notify orchestrator about the count
          replyTo ! ProcessOrchestrator.RowsProcessed(insertedRows)

        }

        //keep consuming
        context.self ! PollRecords(duration, replyTo)

        Behaviors.same

      case Pause(replyTo: ActorRef[ProcessOrchestrator.Orchestrate]) =>
        pauseConsumer(topic, consumer, context, replyTo)

        consumerPaused(connection, topic, table, consumer)

      case UpdateConfig(newProps, replyTo) =>
        val topics = consumer.subscription()

        consumer.close()

        val newConsumer = new KafkaConsumer[String, Film](newProps)

        newConsumer.subscribe(topics)

        replyTo ! ProcessOrchestrator.UpdatedConfig

        kafkaPostgresConnected(connection, topic, table, newConsumer)

      case Stop =>
        consumer.close()

        context.log.info(s"Consumer '${context.self.path.name}' stopped.")

        Behaviors.stopped
    }
  }

  def apply(dbInfo: DatabaseInfo,
            orchestrator: ActorRef[ProcessOrchestrator.Orchestrate],
            consumerProperties: Properties,
            topic: String, table: String): Behavior[Consume] = {

    //make database connection
    val conn: Connection = DriverManager.getConnection(dbInfo.connectionUrl, dbInfo.databaseUser, dbInfo.password)

    //make kafka connection
    val consumer = new KafkaConsumer[String, Film](consumerProperties)

    val topicList = (topic :: Nil) asJava

    consumer.subscribe(topicList)

    logger.info("Connected to the database!")

    kafkaPostgresConnected(conn, topic, table, consumer)
  }
}
