package com.github.massmutual.poc.main

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, DispatcherSelector}
import com.github.massmutual.poc.main.Demo.ApplicationSetting
import com.github.massmutual.poc.main.KafkaToJdbcSink.{Consume, PollRecords}
import com.sun.net.httpserver.HttpExchange
import org.apache.kafka.clients.admin.AdminClient
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.writePretty
import org.slf4j.{Logger, LoggerFactory}

import java.time.Duration
import java.util.Properties
import scala.collection.immutable.Queue

object ProcessOrchestrator {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)

  implicit val formats: Formats = Serialization.formats(NoTypeHints)

  trait Orchestrate

  case object Start extends Orchestrate

  case class ConsumerPaused(consumer: ActorRef[KafkaToJdbcSink.Consume]) extends Orchestrate

  case class RowsProcessed(count: Int) extends Orchestrate

  case object ConsumerResumed extends Orchestrate

  case object StoredProcFinished extends Orchestrate

  case class Status(t: HttpExchange) extends Orchestrate

  case class AddConsumer(name: String, t: HttpExchange) extends Orchestrate

  case class RemoveConsumer(name: String, t: HttpExchange) extends Orchestrate

  case class UpdateConfig(name: String, value: Option[String], t: HttpExchange) extends Orchestrate

  case object UpdatedConfig extends Orchestrate

  private case class BatchStatus(batchSize: Int, timeTaken: Double)

  private def statusReply(t: HttpExchange, currentCount: Int, batchStatusReport: Queue[BatchStatus]): Unit = {

    val jsonBody = batchStatusReport.foldLeft("") { (acc, x) =>
      val json = writePretty(x)

      acc + "," + json
    }

    val response =
      s"""
         |{
         | "currentBatchCount": "$currentCount",
         | "history": [$jsonBody]
         |}
         |""".stripMargin

    val prettyResponse = pretty(render(parse(response))).getBytes

    t.sendResponseHeaders(200, prettyResponse.length)

    val os = t.getResponseBody
    os.write(prettyResponse)
    os.close()
  }

  private def addConsumerReplyAdded(t: HttpExchange, consumers: Set[ActorRef[KafkaToJdbcSink.Consume]]): Unit = {

    val jsonBody = consumers.foldLeft("") { (acc, x) =>
      val json = s""""${x.path.name}"""".stripMargin

      if (acc.isEmpty) json else acc + ", " + json
    }

    val response =
      s"""
         |{
         | "consumers": [$jsonBody]
         |}
         |""".stripMargin

    val prettyResponse = pretty(render(parse(response))).getBytes

    t.sendResponseHeaders(200, prettyResponse.length)

    val os = t.getResponseBody
    os.write(prettyResponse)
    os.close()
  }

  private def addConsumerReplyDuplicate(t: HttpExchange, name: String): Unit = {

    val response =
      s"""
         |{
         | "error": "Consumer '$name' is already added."
         |}
         |""".stripMargin

    val prettyResponse = pretty(render(parse(response))).getBytes

    t.sendResponseHeaders(200, prettyResponse.length)

    val os = t.getResponseBody
    os.write(prettyResponse)
    os.close()
  }

  private def removeConsumerUnknownReply(t: HttpExchange, name: String): Unit = {

    val response =
      s"""
         |{
         | "error": "Consumer '$name' does not exist."
         |}
         |""".stripMargin

    val prettyResponse = pretty(render(parse(response))).getBytes

    t.sendResponseHeaders(200, prettyResponse.length)

    val os = t.getResponseBody
    os.write(prettyResponse)
    os.close()
  }

  private def updateConsumerConfigReply(t: HttpExchange): Unit = {

    val response =
      s"""
         |{
         | "status": "Consumer updated!"
         |}
         |""".stripMargin

    val prettyResponse = pretty(render(parse(response))).getBytes

    t.sendResponseHeaders(200, prettyResponse.length)

    val os = t.getResponseBody
    os.write(prettyResponse)
    os.close()
  }

  //function to call to forward anything to all consumers
  private def sendConsumerMessage(consumers: Set[ActorRef[KafkaToJdbcSink.Consume]], x: KafkaToJdbcSink.Consume): Unit =
    consumers foreach { consumer =>
      consumer ! x
    }

  private def resetTime(): Long = currentTime()

  private def currentTime(): Long = System.currentTimeMillis()

  private def receiveMessage(context: ActorContext[Orchestrate],
                             setting: ApplicationSetting,
                             currentRowCount: Int,
                             startTime: Long,
                             batchStatusReport: Queue[BatchStatus],
                             consumers: Set[ActorRef[KafkaToJdbcSink.Consume]],
                             pausedConsumers: Set[ActorRef[KafkaToJdbcSink.Consume]]): Behaviors.Receive[Orchestrate] = {

    def updateCurrentCount(count: Int): Behavior[Orchestrate] =
      consuming(setting, currentRowCount = count, startTime, batchStatusReport, consumers, pausedConsumers)

    def updatePaused(newPausedList: Set[ActorRef[KafkaToJdbcSink.Consume]]): Behavior[Orchestrate] =
      consuming(setting, currentRowCount, startTime, batchStatusReport, consumers, newPausedList)

    def updateSetting(newSetting: ApplicationSetting): Behavior[Orchestrate] =
      consuming(setting = newSetting, currentRowCount, startTime, batchStatusReport, consumers, pausedConsumers)

    def prepareNewBatch(appendedBatchReport: Queue[BatchStatus]): Behavior[Orchestrate] =
      consuming(setting, currentRowCount = 0, startTime = resetTime(), appendedBatchReport, consumers, pausedConsumers = Set())

    def updateConsumerLists(newConsumerList: Set[ActorRef[KafkaToJdbcSink.Consume]], newPausedList: Set[ActorRef[KafkaToJdbcSink.Consume]]) =
      consuming(setting, currentRowCount, startTime, batchStatusReport, consumers = newConsumerList, pausedConsumers = newPausedList)

    def allPaused(pausedList: Set[ActorRef[KafkaToJdbcSink.Consume]]): Boolean =
      pausedList.size == consumers.size

    def startPolling: PollRecords = PollRecords(Duration.ofMillis(10000), context.self)

    Behaviors.receiveMessage {
      case Start =>
        context.log.info("Beginning consuming...")

        sendConsumerMessage(consumers, startPolling)

        Behaviors.same

      case AddConsumer(name, t) =>
        val alreadyExists = consumers.exists(_.path.name == name)

        if (alreadyExists) {
          addConsumerReplyDuplicate(t, name)
          Behaviors.same
        } else {

          //if currently all the consumers are paused then
          //this actor should not poll any records just yet
          val allArePaused = allPaused(pausedConsumers)

          val newConsumer = createConsumer(context, name, setting)

          val newConsumerList = consumers + newConsumer

          //if not all are paused, start the new consumer
          //AND if all of the existing consumers are paused,
          // pause this one as well so it gets into the correct state
          val newPausedList = if (allArePaused && consumers.nonEmpty) {
            newConsumer ! KafkaToJdbcSink.Pause(context.self)
            pausedConsumers + newConsumer
          } else {
            newConsumer ! startPolling
            pausedConsumers
          }

          addConsumerReplyAdded(t, newConsumerList)

          updateConsumerLists(newConsumerList, newPausedList)
        }

      case UpdateConfig(name, value, t) =>

        value match {
          case Some(v) =>

            val newConsumerProps = setting.consumerProperties

            newConsumerProps.setProperty(name, v)

            val newSetting = setting.copy(consumerProperties = newConsumerProps)

            consumers foreach { x => x ! KafkaToJdbcSink.UpdateConfig(newConsumerProps, context.self) }

            updateConsumerConfigReply(t)

            updateSetting(newSetting)

          case None =>
            Behaviors.same
        }

        Behaviors.same

      case UpdatedConfig =>
        context.log.info("Consumer updated!")

        Behaviors.same

      case RemoveConsumer(name, t) =>
        val foundConsumer = consumers.filter(_.path.name == name)

        val notExist = foundConsumer.isEmpty

        if (notExist) {
          removeConsumerUnknownReply(t, name)
          Behaviors.same
        } else {
          // update original consumer list and remove it from pause list if it exists

          foundConsumer.head ! KafkaToJdbcSink.Stop

          val newConsumerList = consumers - foundConsumer.head

          val newPausedList = pausedConsumers - foundConsumer.head

          addConsumerReplyAdded(t, newConsumerList)

          updateConsumerLists(newConsumerList, newPausedList)
        }

      case RowsProcessed(count) =>
        val newRowCount = currentRowCount + count

        context.log.info(s"total records processed $newRowCount")

        //determine if we want to pause the consumer based on row count
        if (newRowCount >= setting.maxRowCount) {

          context.log.info(s"processing row count reached ($newRowCount)! Pausing consumers")

          sendConsumerMessage(consumers, KafkaToJdbcSink.Pause(context.self))

        }

        updateCurrentCount(newRowCount)

      case ConsumerPaused(consumer) =>
        context.log.info(s"consumer paused ${consumer.path}")

        //add to paused list
        val newPausedList = pausedConsumers + consumer

        val allArePaused = allPaused(newPausedList)

        //if all the consumers are paused, tell the stored procedure runner to do its thing
        if (allArePaused) {

          context.log.info(s"Initiating stored procedure at batch count: $currentRowCount")

          //pick a random consumer to do the stored proc
          newPausedList.head ! KafkaToJdbcSink.BeginStoreProc(context.self)
        }

        updatePaused(newPausedList)

      case ConsumerResumed =>
        context.log.info("Consumer resumed! Start polling again")

        val poll = PollRecords(Duration.ofMillis(10000), context.self)

        sendConsumerMessage(consumers, poll)

        Behaviors.same

      case StoredProcFinished =>
        context.log.info(s"stored proc finished!")

        //resume the consumers
        pausedConsumers foreach { x =>
          x ! KafkaToJdbcSink.Resume(context.self)
        }

        val timeTaken = (currentTime() - startTime) / 1000.0

        val batchStatus: BatchStatus = BatchStatus(currentRowCount, timeTaken)

        val removedOld: Queue[BatchStatus] = if (batchStatusReport.size >= setting.historySize) {
          val (_, removed) = batchStatusReport.dequeue
          removed
        } else batchStatusReport

        val appendedBatchReport = removedOld.enqueue(batchStatus)

        //reset everything and prepare for new batch
        prepareNewBatch(appendedBatchReport)

      case Status(t) =>
        statusReply(t, currentRowCount, batchStatusReport)
        Behaviors.same
    }
  }

  def consuming(setting: ApplicationSetting,
                currentRowCount: Int,
                startTime: Long,
                batchStatusReport: Queue[BatchStatus],
                consumers: Set[ActorRef[KafkaToJdbcSink.Consume]],
                pausedConsumers: Set[ActorRef[KafkaToJdbcSink.Consume]]): Behavior[Orchestrate] = Behaviors.setup { context =>
    receiveMessage(context, setting, currentRowCount, startTime, batchStatusReport, consumers, pausedConsumers)
  }

  /**
   * Creates a new consumer with specified `consumerClientId`.
   * This means the actor name with be `consumerClientId` and the consumer client's name
   * is going to be `consumerClientId`.
   *
   * @param context
   * @param consumerClientId
   * @param setting
   * @return
   */
  private def createConsumer(context: ActorContext[Orchestrate], consumerClientId: String, setting: ApplicationSetting): ActorRef[Consume] = {

    val withConsumerClientId = {
      val prop = setting.consumerProperties

      prop.setProperty("client.id", consumerClientId)

      setting.copy(consumerProperties = prop)
    }

    //spawn a child actor that does consumption from kafka
    val consumerActor: Behavior[Consume] =
      KafkaToJdbcSink(setting.dbInfo, context.self, withConsumerClientId.consumerProperties, setting.topic, setting.table)

    //since the consumer is blocking, select a separate pool
    val dispatcher = DispatcherSelector.fromConfig("kafka-sink-dispatcher")

    context.spawn[Consume](consumerActor, consumerClientId, dispatcher)
  }

  def apply(setting: ApplicationSetting): Behavior[Orchestrate] = Behaviors.setup { context =>

    val server: Behavior[Server.Serve] = Server(context.self)

    val serverActor = context.spawn[Server.Serve](server, "Server", DispatcherSelector.fromConfig("server-dispatcher"))

    //first start the health server
    serverActor ! Server.Start

    //define a default list of consumers
    val consumers: Set[ActorRef[KafkaToJdbcSink.Consume]] = (0 until setting.consumerCount) map { x =>
      createConsumer(context, consumerClientId = s"DefaultConsumer$x", setting)
    } toSet

    receiveMessage(context, setting, currentRowCount = 0, startTime = resetTime(), batchStatusReport = Queue(), consumers, pausedConsumers = Set())
  }
}
