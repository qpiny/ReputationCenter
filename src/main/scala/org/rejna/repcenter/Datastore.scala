package org.rejna.repcenter

import java.util.Date

import scala.concurrent.{ ExecutionContext, Future }

//import akka.actor.ActorSystem
import akka.actor.Extension
import akka.actor.ActorSystem
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.actor.ExtendedActorSystem
import akka.event.{ Logging, LogSource }

import play.api.libs.iteratee.Iteratee

import reactivemongo.api._
import reactivemongo.bson._
import reactivemongo.core.commands.LastError

object Datastore extends ExtensionId[DatastoreImpl] with ExtensionIdProvider {
  override def lookup = Datastore
  override def createExtension(system: ExtendedActorSystem) = new DatastoreImpl(system)
  override def get(system: ActorSystem): DatastoreImpl = super.get(system)
}

class DatastoreImpl(system: ExtendedActorSystem) extends Extension {
  import LogSource.fromString
  implicit val executionContext = system.dispatcher
  implicit val listReader = new ListReader(system)

  val log = Logging(system, "Datastore")
  val driver = new MongoDriver
  val connection = driver.connection(List("localhost"))
  val listdb = connection("reputationList")
  val listCollection = listdb("list")
  val objCollection = listdb("objects")

  def getLists = {
    listCollection.find(BSONDocument())
      .cursor[BSONDocument]
      .collect[List]()
      .map(_.map(_.as[ReputationList]))
  }

  def update(list: ReputationList): BSONDocument => Unit = {
    val now = BSONDateTime(new Date().getTime)
    listCollection.find(BSONDocument("name" -> list.name))
      .one
      .map(_.flatMap(
        _.get("_id")) match {
          case Some(listId) =>
            listCollection.update(
              BSONDocument("id" -> listId),
              BSONDocument("lastUpdate" -> now))
          case None =>
            val listDocument = BSONDocument(
              "name" -> list.name,
              "url" -> list.url,
              "confidence" -> list.confidence,
              "firstUpade" -> now,
              "lastUpdate" -> now)
            listCollection.insert(listDocument)
        })
      .onFailure {
        case t => log.error("Fail to update list {} : {}", list.name, t)
      }

    (obj) =>
      val query = BSONDocument(list.idFields.flatMap(f => obj.get(f).map(f -> _)))
      objCollection
        .update(query, BSONDocument("$set" -> (query ++ BSONDocument("lastInsert" -> now, "list" -> list.name))))
        .flatMap {
          case le @ LastError(_, _, _, _, _, _, true) =>
            println("Record updated")
            Future.successful(le)
          case LastError(_, _, _, _, _, _, false) =>
            println("Record not found")
            objCollection.insert(query ++ BSONDocument("lastInsert" -> now, "firstInsert" -> now, "list" -> list.name))
        }
        .onFailure {
          case t =>
            log.error("Fail to insert record in list {} : {}", list.name, t)
        }

  }

  def search(field: String, value: String, historyLabel: Option[String]): Future[List[BSONDocument]] = {
    search(BSONDocument(field -> value), historyLabel)
  }

  def search(doc: BSONDocument, historyLabel: Option[String]): Future[List[BSONDocument]] = {
    val result = objCollection.find(doc).cursor.collect[List]()
    historyLabel.map { label =>
      val now = BSONDateTime(new Date().getTime)
      result.map { r =>
        objCollection.insert(doc ++ BSONDocument(
          "list" -> "history",
          "firstInsert" -> now,
          "label" -> label,
          "result" -> BSONArray(r)))
      }
    }
    result
  }
  // TODO def search() with criteria
  // search will add item in database
}

