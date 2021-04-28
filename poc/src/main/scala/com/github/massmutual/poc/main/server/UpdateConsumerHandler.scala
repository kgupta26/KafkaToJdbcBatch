package com.github.massmutual.poc.main.server

import akka.actor.typed.ActorRef
import com.github.massmutual.poc.main.ProcessOrchestrator
import com.sun.net.httpserver.{HttpExchange, HttpHandler}

case class UpdateConsumerHandler(orch: ActorRef[ProcessOrchestrator.Orchestrate]) extends HttpHandler {
  def handle(t: HttpExchange) {
    val config = t.getRequestURI.getRawPath.split("/").last
    val parts = config.split("=")

    if (parts.length != 2) {
      val prettyResponse = "Please provide config in form of 'some.kafka.config=2' ".getBytes

      t.sendResponseHeaders(200, prettyResponse.length)

      val os = t.getResponseBody
      os.write(prettyResponse)
      os.close()
    } else {
      val Array(name, value) = parts

      orch ! ProcessOrchestrator.UpdateConfig(name, Some(value), t)
    }
    //todo unit test
  }
}

