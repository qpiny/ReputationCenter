package org.rejna.repcenter

import scala.tools.nsc.interpreter._
import scala.tools.nsc.Settings

import reactivemongo.api._
import reactivemongo.bson._

object MongoTest extends App {
  
  override def main(args: Array[String]) = {
    val driver = new MongoDriver
    val connection = driver.connection(List("localhost:28017"))
    
    val settings = new Settings
    settings.usejavacp.value = true
    new ILoop().process(settings)
  }

}