package controllers

import java.time.Duration
import java.util.concurrent
import javax.inject.Inject

import akka.actor.Actor.Receive
import akka.actor._
import akka.pattern.ask
import akka.stream._
import akka.util.Timeout

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import play.api._
import play.api.libs.ws._
import play.api.mvc._
import play.api.libs.json.Json
import play.api.libs.streams.ActorFlow

import scala.concurrent.duration._
import scala.util.{Success, Try}

class QueueController @Inject() (ws:WSClient)(implicit system: ActorSystem, materializer: Materializer) extends Controller {
  def getGlicko(steamId:String):Future[Double] = {
    val url = s"http://qlstats.net/player/${steamId}.json"
    val request = ws.url(url)
    Logger.info(s"getting glicko from ${url}")
    return request.get().map(response => {
      (Try((response.json \ 0 \ "elos" \ "duel" \ "g2_r").as[Double])).getOrElse(0.0)
    })
  }

  def queueSocket = {
    WebSocket.acceptOrResult[String, String] { case request =>
      request.session.get("steamUserInfo") match {
        case None => {
          Logger.info(s"can not get steamUserInfo, forbid access, session is ${request.session.data}")
          Future.successful(Left(Forbidden.withNewSession))
        }
        case Some(userInfoJs) =>
          SteamUserInfo.fromJson(userInfoJs).map(
            userInfo => {
              Logger.info(s"creating websocket for ${userInfo}")
              getGlicko(userInfo.steamId).map(glicko => {
                Logger.info(s"got glicko ${glicko} for user ${userInfo.steamId}")
                Right(ActorFlow.actorRef(out => QueueWebSocketAcceptor.props(out, userInfo, glicko)))
            })})
            .orElse({
              Logger.info("access denied - can not parse SteamUserInfo")
              Some(Future.successful(Left(Forbidden.withNewSession)))
            }).get
      }
    }
  }
}


object QueueMessages {
  class QueueMessage
  case class Enqueued() extends QueueMessage
  case class NewChallenge(server:String) extends QueueMessage
  case class NoCompetition() extends QueueMessage

  def serialize(queueMessage: QueueMessage):String = queueMessage match {
    case msg:Enqueued =>
      Json.obj("cmd" -> "enqueued").toString()
    case msg:NoCompetition =>
      Json.obj("cmd" -> "noCompetition").toString()

    case msg:NewChallenge =>
      implicit val writer = Json.writes[NewChallenge]
      Json.obj("cmd" -> "newChallenge",
        "body" -> Json.toJson(msg)).toString()
  }

  def deserialize(jss:String):Option[QueueMessage] = {
    val js = Json.parse(jss)
    val cmd = (js \ "cmd").as[String]
    cmd match {
      case "enqueued" => Some(Enqueued())
      case "noCompetition" => Some(NoCompetition())
      case "newChallenge" =>
        implicit val reader = Json.reads[NewChallenge]
        (js \ "body").toOption.flatMap(v => Json.fromJson[NewChallenge](v).asOpt)
    }
  }
}


object QueueWebSocketAcceptor {
  def props(out: ActorRef, userInfo:SteamUserInfo, glicko:Double) = Props(new QueueWebSocketAcceptor(out, userInfo, glicko))
}

class QueueWebSocketAcceptor(out:ActorRef, userInfo:SteamUserInfo, glicko:Double) extends Actor {
  val remoteQueuePath = context.system.settings.config.getString("q3mm.queueUri")
  val queueProxy = context.actorSelection(remoteQueuePath)

  override def receive: Receive = {
    case msgJs:String =>
      val msg = QueueMessages.deserialize(msgJs)
      Logger.info(s"received $msg")
  }

  override def postStop(): Unit = {
    queueProxy ! ("dequeue", userInfo.steamId)
  }

  @scala.throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    implicit val timeout = Timeout(60 seconds)
    out ! QueueMessages.serialize(QueueMessages.Enqueued())
    val res = ask(queueProxy, ("enqueue", userInfo, glicko))
    res.map({case ("challenge", server:String) =>
      Logger.info(s"got challenge at $server")
      out ! QueueMessages.serialize(QueueMessages.NewChallenge(server))
    })
      .recover { case _ =>
        out ! QueueMessages.serialize(QueueMessages.NoCompetition())
        out ! PoisonPill
      }
  }
}
