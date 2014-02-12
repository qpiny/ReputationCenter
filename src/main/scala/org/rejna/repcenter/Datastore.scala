package org.rejna.repcenter

import java.util.Date

import scala.concurrent.{ ExecutionContext, Future }

import akka.actor.ActorSystem

import reactivemongo.api._
import reactivemongo.bson._
import reactivemongo.core.commands.LastError

class Datastore(implicit val executionContext: ExecutionContext) {
  val driver = new MongoDriver
  val connection = driver.connection(List("localhost"))
  val listdb = connection("reputationList")
  val listCollection = listdb("list")
  val objCollection = listdb("objects")

  def update(list: ReputationList): BSONDocument => Unit = {
    val now = BSONDateTime(new Date().getTime)
    listCollection.update(
      BSONDocument("name" -> list.name),
      BSONDocument("$set" -> BSONDocument("lastUpdate" -> now)))
      .flatMap {
        case le @ LastError(_, _, _, _, _, _, true) =>
          Future.successful(le)
        case LastError(_, _, _, _, _, _, false) =>
          listCollection.insert(BSONDocument(
            "name" -> list.name,
            "url" -> list.url,
            "confidence" -> list.confidence,
            "firstUpade" -> now,
            "lastUpdate" -> now))
      }
      .onFailure {
        case t => println("List insert failed : " + t)
      }

    (obj) =>
      val query = BSONDocument(list.idFields.flatMap(f => obj.get(f).map(f -> _)))
      objCollection
        .update(query, BSONDocument("$set" -> (query ++ BSONDocument("lastInsert" -> now))))
        .flatMap {
          case le @ LastError(_, _, _, _, _, _, true) =>
            println("Record updated")
            Future.successful(le)
          case LastError(_, _, _, _, _, _, false) =>
            println("Record not found")
            objCollection.insert(query ++ BSONDocument("lastInsert" -> now, "firstInsert" -> now))
        }
        .onFailure {
          case t => println("Record insert failed : " + t)
        }
  }

  // TODO def search() with criteria
  // search will add item in database
}

