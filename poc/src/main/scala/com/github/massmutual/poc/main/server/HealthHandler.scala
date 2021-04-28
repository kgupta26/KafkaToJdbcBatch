package com.github.massmutual.poc.main.server

import com.sun.net.httpserver.{HttpExchange, HttpHandler}

case class HealthHandler() extends HttpHandler {
  def handle(t: HttpExchange) {
    sendResponse(t)
  }

  private def sendResponse(t: HttpExchange): Unit = {
    val response = "HEALTHY".getBytes

    t.sendResponseHeaders(200, response.length)

    val os = t.getResponseBody
    os.write(response)
    os.close()
  }
}