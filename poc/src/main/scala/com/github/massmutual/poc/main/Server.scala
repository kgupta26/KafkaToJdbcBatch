package com.github.massmutual.poc.main

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.github.massmutual.poc.main.server.{AddConsumerHandler, HealthHandler, RemoveConsumerHandler, StatusHandler, UpdateConsumerHandler}
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}

import java.net.InetSocketAddress

object Server {

  trait Serve

  case object Start extends Serve

  def startServer(orch: ActorRef[ProcessOrchestrator.Orchestrate]): Unit = {
    val server = HttpServer.create(new InetSocketAddress(8080), 0)
    server.createContext("/health", HealthHandler())
    server.createContext("/status", StatusHandler(orch))
    server.createContext("/consumer/add/", AddConsumerHandler(orch))
    server.createContext("/consumer/remove/", RemoveConsumerHandler(orch))
    server.createContext("/config/update/", UpdateConsumerHandler(orch))
    server.setExecutor(null)

    server.start()
  }

  def apply(orch: ActorRef[ProcessOrchestrator.Orchestrate]): Behavior[Serve] = Behaviors.setup { context =>

    context.log.info("Starting server...")

    Behaviors.receiveMessage {
      case Start =>
        startServer(orch)
        context.log.info("Server started. ")
        Behaviors.same
    }

  }
}

