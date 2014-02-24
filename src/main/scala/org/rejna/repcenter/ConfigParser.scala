package org.rejna.repcenter

import java.io.{ Reader => JavaReader }

import scala.util.matching.Regex
import scala.util.parsing.combinator._

import akka.actor.ActorSystem

import reactivemongo.bson.BSONDocument

class ListParameters(name: String) {
  var url = ""
}

object ConfigParser extends JavaTokenParsers {
  var listParameters: Option[ListParameters] = None
  
  def stringLiteralContent: Parser[String] = stringLiteral ^^ { _.drop(1).dropRight(1) }

  def urlParameter = "url" ~ "=" ~> stringLiteralContent ^^ ("url" -> _)
  def confidenceParameter = "confidence" ~ "=" ~> wholeNumber ^^ { "confidence" -> _.toInt }
  //def listParameters = urlParameter /* | parserParameter*/ | confidenceParameter
  
  def inList = Parser { in =>
    listParameters match {
      case Some(p) => Success(p, in)
      case _ => Failure("Not in list context", in)
    }
  }

  
  def listDefinitionStart(implicit system: ActorSystem) = "define(" ~> stringLiteralContent <~ ")" ~ "{" ^^ {
    case name => listParameters = Some(new ListParameters(name))
  }
  
  def listDefinitionStop = inList <~ "}" ^^ {
    case name ~ _ ~ _ ~ parameterList =>
      val params = parameterList.toMap
      val parsers = params.getOrElse("parsers", sys.error("parsers not present in configuration"))
        .asInstanceOf[(List[String], Parser[BSONDocument])]

      ReputationList(
        name,
        url = params.getOrElse("url", sys.error("url not present in configuration")).asInstanceOf[String],
        confidence = params.getOrElse("confidence", 50).asInstanceOf[Int],
        fields = parsers._1,
        parsers = parsers._2)
  }

  def loadConfiguration(in: JavaReader)(implicit system: ActorSystem) = {
    parseAll(rep(listDefinition), in)
  }
}