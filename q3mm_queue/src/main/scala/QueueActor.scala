import akka.actor._
import akka.event.Logging
import akka.http.javadsl.unmarshalling.Unmarshaller
import akka.http.scaladsl.Http
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse}
import akka.pattern.ask
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.util.Timeout

import scala.util._
import scala.concurrent.duration._
import controllers.SteamUserInfo
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.Future

class QueueActor extends Actor {
  private val log = Logging(context.system, this)
  private val instanceCreationTimeout = 360 seconds
  private val remoteInstanceMasterPath = context.system.settings.config.getString("q3mm.instanceMasterUri")
  private val instanceMasterProxy = context.actorSelection(remoteInstanceMasterPath)

  private case class GameRequest(userInfo:SteamUserInfo, glicko:Double, requester:ActorRef)
  private case class Tick()

  import context.dispatcher
  private val ticker = context.system.scheduler.schedule(30 seconds, 30 seconds, self, Tick())

  private var queue = Map.empty[String, GameRequest]
  private val maxGlickoGap = 300

  override def receive: Receive = {
    case ("enqueue", steamUserInfo:SteamUserInfo, glicko:Double) =>
      log.info(s"enqueuing ${steamUserInfo} with glicko ${glicko}")
      queue += ((steamUserInfo.steamId, GameRequest(steamUserInfo, glicko, sender())))

    case ("dequeue", steamId:String) =>
      log.info(s"dequeuing $steamId")
      queue -= steamId

    case Tick() =>
      // select optimal matches
      val sortedPlayers = queue.values.toStream.sortBy(_.glicko).toList
      val playerPairs = sortedPlayers.zip(sortedPlayers.tail).toList
      val matches = playerPairs.foldLeft((List.empty[(GameRequest, GameRequest)], false)) (
        {(acc, pair:(GameRequest, GameRequest)) => acc match {
          case (pairs, true) => (pairs, false)
          case (pairs, false) =>
            if ((pair._1.glicko - pair._2.glicko).abs < maxGlickoGap)
              (pair::pairs, true)
            else
              (pairs, false)
      }})._1
      // match make
      matches.foreach(
        { case (leftReq, rightReq) =>
          log.info(s"matched ${leftReq.userInfo.steamId} with ${rightReq.userInfo.steamId}")
          queue -= leftReq.userInfo.steamId
          queue -= rightReq.userInfo.steamId
          implicit val timeout = Timeout(instanceCreationTimeout)
          ask(instanceMasterProxy, ("requestServer", leftReq.userInfo, rightReq.userInfo))
            .onComplete({
              case Failure(ex)=> Seq(leftReq.requester, rightReq.requester).foreach(_ ! ("failed", ex))
              case Success(("created", serverAddress)) =>
                val challenge = ("challenge", serverAddress)
                Seq(leftReq.requester, rightReq.requester).foreach(_ ! challenge)
              case Success(("failed", error)) =>
                Seq(leftReq.requester, rightReq.requester).foreach(_ ! ("failed", error))
              case _ => ???
            })
        }
      )
  }
}
