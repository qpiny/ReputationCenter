package org.rejna.repcenter

import scala.concurrent.ExecutionContext

import akka.actor.ActorSystem

import reactivemongo.api._
import reactivemongo.bson._

class Datastore(implicit val executionContext: ExecutionContext) {
  val driver = new MongoDriver
  val connection = driver.connection(List("localhost"))
  val listdb = connection("reputationList")
  val listCollection = listdb("list")
  val objCollection = listdb("objects")

  def get(list: ReputationList) = {

  }
  class Liststore(list: ReputationList) {

    def update(obj: Map[String, String]) = {
      
      val query = BSONDocument(obj.collect {
        case (f, v) if list.fields contains f => f -> BSONString(v)
      })
      
      val a = objCollection.find(query).cursor.headOption.map {
        case Some(d) => 
        case None => 
      }
        case (field, value) => (field, value)
      }))

    }

  }
}

