package org.rejna.repcenter

import java.util.Date

import scala.util.{ Success, Failure }
import scala.tools.nsc.interpreter._
import scala.tools.nsc.Settings

import akka.actor.ActorSystem

import reactivemongo.api._
import reactivemongo.bson._
import reactivemongo.core.commands.LastError

import play.api.libs.iteratee._

object MongoTest extends App {

  override def main(args: Array[String]) = {
    implicit val system = ActorSystem()
    implicit val executionContext = system.dispatcher

    val driver = new MongoDriver
    val connection = driver.connection(List("localhost:27017"))
    val testdb = connection("testdb")
    val objCollection = testdb("objects")

    val now = BSONDateTime(new Date().getTime)
    val query = BSONDocument(
      "field1" -> "value1",
      "field2" -> "value2")

    val printDocument: Iteratee[BSONDocument, Unit] = Iteratee.foreach { doc =>
      println("--- document list ---")
      println(BSONDocument.pretty(doc))
    }

    objCollection
      .update(query, BSONDocument("$set" -> (query ++ BSONDocument("lastInsert" -> now, "a" -> "c"))))
      .flatMap {
      case LastError(_, _, _, _, _, _, true) =>
        println("Record updated")
        objCollection.find(query).cursor.enumerate().apply(printDocument)
      case LastError(_, _, _, _, _, _, false) =>
        println("Record not found")
        objCollection.insert(query ++ BSONDocument("lastInsert" -> now, "firstInsert" -> now))
        objCollection.find(query).cursor.enumerate().apply(printDocument)
      }
      .onComplete {
        case Success(x) => println("Success")
        case Failure(t) => t.printStackTrace()
      }

    //      headOption.map {
    //      case Some(d) => objCollection.update(query, BSONDocument("$set" -> query))
    //      case None => query ++ BSONDocument("firstInsert" -> now)
    //    }
    //    val settings = new Settings
    //    settings.usejavacp.value = true
    //    new ILoop().process(settings)
  }

}