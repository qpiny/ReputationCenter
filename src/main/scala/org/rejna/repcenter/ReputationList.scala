package org.rejna.repcenter

import scala.language.postfixOps
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.actor.ActorDSL._
import akka.util.Timeout
import akka.io.IO
import akka.event.slf4j.Logger

import spray.can.Http
import spray.http.{ HttpRequest, HttpResponse, Uri }
import spray.http.HttpMethods.GET

import ListParser.{ Success, NoSuccess }

case class ReputationList(name: String, url: String, confidence: Int, fields: List[String], parsers: ListParser.Parser[Map[String, String]])(implicit val system: ActorSystem) {
  import system.dispatcher // execution context for futures
  implicit val timeout = Timeout(10 seconds)
  val log = Logger(this.getClass, name)

  def get() = {
    actor(new Act {
      IO(Http) ! HttpRequest(GET, Uri(url))

      become {
        case HttpResponse(status, entity, header, protocol) =>
          val lines = entity.asString.split("\\r?\\n").map(_.trim).filter(!_.isEmpty)
          for (line <- lines) {
            ListParser.parseAll(parsers, line) match {
              case NoSuccess(message, _) => log.error("Parse error : " + message)
              case Success(result, _) => log.info(result.toString)

            }
          }
        case m: Any => println(s"Receive ${m.getClass.getName} : ${m}")
      }
    })
  }
}