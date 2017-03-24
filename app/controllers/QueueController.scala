package controllers

import java.time.Duration
import java.util.concurrent
import java.util.concurrent.TimeoutException
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
import scala.util.{Failure, Success, Try}

class QueueController @Inject() (ws:WSClient, configuration: play.api.Configuration)
                                (implicit system: ActorSystem, materializer: Materializer) extends Controller {
  def getGlicko(steamId:String):Future[Double] = {
    val url = s"http://qlstats.net/player/${steamId}.json"
    val request = ws.url(url)
    Logger.info(s"getting glicko from ${url}")
    request.get().map(response => {
      Try((response.json \ 0 \ "elos" \ "duel" \ "g2_r").as[Double])
        .orElse(Try((response.json \ 0 \ "elos" \ "ffa" \ "g2_r").as[Double]))
        .getOrElse(1500.0)
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

  def matchMake = Action {
    request => {
      implicit val reads = Json.reads[SteamUserInfo]
      Ok(views.html.matchMake(
        request.session.get("steamUserInfo").flatMap(v =>
          SteamUserInfo.fromJson(v)),
        configuration.underlying.getString("q3mm.wsUrl")))
    }
  }
}


object QueueMessages {
  class QueueMessage
  case class Enqueued() extends QueueMessage
  case class NewChallenge(server:String) extends QueueMessage
  case class NoCompetition(reason:String) extends QueueMessage

  def serialize(queueMessage: QueueMessage):String = queueMessage match {
    case msg:Enqueued =>
      Json.obj("cmd" -> "enqueued").toString()
    case msg:NoCompetition =>
      Json.obj("cmd" -> "noCompetition",
      "reason" -> msg.reason).toString()

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
      case "noCompetition" => (js \ "reason").toOption.map(_.as[String]).map(NoCompetition.apply)
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
  private val remoteQueuePath = context.system.settings.config.getString("q3mm.queueUri")
  private val queueProxy = context.actorSelection(remoteQueuePath)

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
    implicit val timeout = Timeout(20 minutes)
    out ! QueueMessages.serialize(QueueMessages.Enqueued())
    val res = queueProxy.resolveOne(5 seconds).flatMap(qp => ask(qp, ("enqueue", userInfo, glicko)))
    res.onComplete({
      case Success(("challenge", server:String)) =>
        Logger.info(s"got challenge at $server")
        out ! QueueMessages.serialize(QueueMessages.NewChallenge(server))
      case Success(("failed", reason:String)) =>
        Logger.info(s"failed with $reason")
        out ! QueueMessages.serialize(QueueMessages.NoCompetition(reason))
      case Failure(reason) =>
        Logger.info(s"failed with $reason")
        reason match {
          case _:TimeoutException => out ! QueueMessages.serialize (QueueMessages.NoCompetition("no competition") )
          case _ => out ! QueueMessages.serialize (QueueMessages.NoCompetition("internal server error") )
        }
        out ! PoisonPill
      case _ => ???
    })
  }
}
