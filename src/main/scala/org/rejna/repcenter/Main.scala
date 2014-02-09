package org.rejna.repcenter

import java.io.FileReader

import akka.actor.ActorSystem
import akka.event.slf4j.Logger

import ListParser.{ Success, NoSuccess}

object Main extends App {

  val log = Logger(this.getClass, "main")
  
  override def main(args: Array[String]) = {
    super.main(args)
    
    implicit val system = ActorSystem()
    
    ListParser.loadConfiguration(new FileReader("src/main/resources/reputation.conf")) match {
      case Success(result, _) =>
        for (replist <- result) {
          println(s"list ${replist.name}")
          replist.get
        }
      case NoSuccess(message, _) => log.error("Configuration parsing failed : " + message)
    }
  }
}