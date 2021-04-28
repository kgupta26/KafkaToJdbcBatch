package com.github.massmutual.poc.main.server

import akka.actor.typed.ActorRef
import com.github.massmutual.poc.main.ProcessOrchestrator
import com.sun.net.httpserver.{HttpExchange, HttpHandler}

case class StatusHandler(orch: ActorRef[ProcessOrchestrator.Orchestrate]) extends HttpHandler {
  def handle(t: HttpExchange) {
    askStatus(t)
  }

  private def askStatus(t: HttpExchange): Unit = {
    orch ! ProcessOrchestrator.Status(t)
  }
}
