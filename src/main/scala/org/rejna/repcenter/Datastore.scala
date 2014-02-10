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

  def get(list: ReputationList) = {

  }
  class Liststore(list: ReputationList) {

    def update(obj: BSONDocument) = {
      val query = BSONDocument(list.idFields.flatMap(f => obj.get(f).map(f -> _)))
      val now = BSONDateTime(new Date().getTime)

      objCollection
        .update(query, BSONDocument("$set" -> (query ++ BSONDocument("lastInsert" -> now, "a" -> "c"))))
        .flatMap {
          case le @ LastError(_, _, _, _, _, _, true) =>
            println("Record updated")
            Future.successful(le)
          case LastError(_, _, _, _, _, _, false) =>
            println("Record not found")
            objCollection.insert(query ++ BSONDocument("lastInsert" -> now, "firstInsert" -> now))
        }
        .onFailure {
          case t => println("Insert failed : " + t)
        }
    }
  }
}

