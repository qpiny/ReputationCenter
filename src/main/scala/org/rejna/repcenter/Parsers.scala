package org.rejna.repcenter

import java.io.Reader

import scala.util.matching.Regex
import scala.util.parsing.combinator._

import akka.actor.ActorSystem

import reactivemongo.bson._

object ListParser extends JavaTokenParsers {
  def stringLiteralContent: Parser[String] = stringLiteral ^^ { _.drop(1).dropRight(1) }

  def domain: Parser[BSONDocument] = """(?:(?:[\p{Graph}&&[^\.]])+\.)+\p{Alpha}+""".r ^^ { value => BSONDocument("domain" -> BSONString(value)) }
  def ip: Parser[BSONDocument] = """\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}""".r ^^ { value => BSONDocument("ip" -> BSONString(value)) }
  def comment(s: String): Parser[BSONDocument] = s ~ ".*".r ^^ { _ => BSONDocument.empty }

  def domainParser = "domain" ^^ { _ => ("domain" :: Nil, domain) }
  def ipParser = "ip" ^^ { _ => ("ip" :: Nil, ip) }
  def commentParser = "comment" ~> opt("(" ~> stringLiteralContent <~ ")") ^^ { c => (Nil, comment(c.getOrElse("#"))) }
  def parsers = domainParser | ipParser | commentParser

  def parserParameter = "parsers" ~ "=" ~> rep1sep(parsers, ",") ^^ {
    "parser" -> _.reduce((p1, p2) => (p1._1 ::: p2._1, p1._2 | p2._2))
  }
  def urlParameter = "url" ~ "=" ~> stringLiteralContent ^^ ("url" -> _)
  def confidenceParameter = "confidence" ~ "=" ~> wholeNumber ^^ { "confidence" -> _.toInt }
  def listParameters = urlParameter | parserParameter | confidenceParameter
  def listDefinition(implicit system: ActorSystem) = "define(" ~> stringLiteralContent ~ ")" ~ "{" ~ rep(listParameters) <~ "}" ^^ {
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

  def loadConfiguration(in: Reader)(implicit system: ActorSystem) = {
    parseAll(rep(listDefinition), in)
  }
}