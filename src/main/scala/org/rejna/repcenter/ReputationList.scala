package org.rejna.repcenter

import scala.language.postfixOps
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.actor.ActorDSL._
import akka.util.Timeout
import akka.io.IO
import akka.event.slf4j.Logger

import spray.can.Http
import spray.http._
import spray.http.HttpMethods.GET

import reactivemongo.bson._

import ListParser.{ Success, NoSuccess }

class ListReader(system: ActorSystem) extends BSONDocumentReader[ReputationList] {
  def read(doc: BSONDocument): ReputationList = {
    val name = doc.getAs[String]("name").get
    val url = doc.getAs[String]("url").get
    val confidence = doc.getAs[Int]("confidence").get
    val parsersString = doc.getAs[String]("parsers").get
    val (fields, parsers) = ListParser.parseAll(ListParser.parsers, parsersString).get
    ReputationList(name, url, confidence, fields, parsers)(system)
  }
}
case class ReputationList(name: String, url: String, confidence: Int, fields: List[String], parsers: ListParser.Parser[BSONDocument])(implicit val system: ActorSystem) { list =>
  import system.dispatcher
  implicit val timeout = Timeout(10 seconds)
  val log = Logger(this.getClass, name)

  def idFields = fields

  def get(datastore: DatastoreImpl) = {
    actor(new Act {
      IO(Http) ! HttpRequest(GET, Uri(url))
      val ds = datastore.update(list)

      become {
        case ChunkedResponseStart(HttpResponse(status, entity, header, protocol)) => entity.asString
        case MessageChunk(data, extension) => data.asString
        case c: ChunkedMessageEnd =>
        case HttpResponse(status, entity, header, protocol) =>
          val lines = entity.asString.split("\\r?\\n").map(_.trim).filter(!_.isEmpty)
          for (line <- lines) {
            ListParser.parseAll(parsers, line) match {
              case NoSuccess(message, _) => log.error("Parse error : " + message)
              case Success(result, _) => ds(result)
            }
          }
        case m: Any => println(s"Receive ${m.getClass.getName} : ${m}")
      }
    })
  }
}