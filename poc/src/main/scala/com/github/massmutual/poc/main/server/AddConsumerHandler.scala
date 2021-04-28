package com.github.massmutual.poc.main.server

import akka.actor.typed.ActorRef
import com.github.massmutual.poc.main.ProcessOrchestrator
import com.sun.net.httpserver.{HttpExchange, HttpHandler}

case class AddConsumerHandler(orch: ActorRef[ProcessOrchestrator.Orchestrate]) extends HttpHandler {
  def handle(t: HttpExchange) {
    val name = t.getRequestURI.getRawPath.split("/").last
    //todo unit test
    orch ! ProcessOrchestrator.AddConsumer(name, t)
  }
}

