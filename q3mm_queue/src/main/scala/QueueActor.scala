import akka.actor._
import akka.event.Logging
import akka.pattern.ask
import akka.util.Timeout

import scala.util._
import scala.concurrent.duration._
import controllers.{QueueStats, SteamUserInfo}
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.Future

class QueueActor extends Actor {
  private val log = Logging(context.system, this)
  private val instanceCreationTimeout = 360 seconds
  private val remoteInstanceMasterPath = context.system.settings.config.getString("q3mm.instanceMasterUri")
  private val instanceMasterProxy = context.actorSelection(remoteInstanceMasterPath)

  private case class GameRequest(userInfo:SteamUserInfo, glicko:Double, requester:ActorRef, maxGlickoGap:Int)
  private case class Tick()

  import context.dispatcher
  private val ticker = context.system.scheduler.schedule(30 seconds, 30 seconds, self, Tick())

  private var queue = Map.empty[String, GameRequest]
  private val maxGlickoGap = 300

  override def receive: Receive = {
    case ("enqueue", steamUserInfo:SteamUserInfo, glicko:Double) =>
      log.info(s"enqueuing ${steamUserInfo} with glicko ${glicko} and max gap ${maxGlickoGap}")
      queue += ((steamUserInfo.steamId, GameRequest(steamUserInfo, glicko, sender(), maxGlickoGap)))

    case ("dequeue", steamId:String) =>
      log.info(s"dequeuing $steamId")
      queue -= steamId

    case "stats" =>
      log.info("gathering backend stats")
      sender() ! QueueStats(queue.values.map(u => (u.userInfo, u.glicko)).toList)

    case Tick() =>
      // select optimal matches
      val sortedPlayers = queue.values.toStream.sortBy(_.glicko).toList
      val playerPairs = sortedPlayers.zip(if (sortedPlayers.isEmpty) sortedPlayers else sortedPlayers.tail)
      val matches = playerPairs.foldLeft((List.empty[(GameRequest, GameRequest)], false)) (
        {(acc, pair:(GameRequest, GameRequest)) => acc match {
          case (pairs, true) => (pairs, false)
          case (pairs, false) =>
            val glickoGap = (pair._1.glicko - pair._2.glicko).abs
            if (glickoGap < pair._1.maxGlickoGap && glickoGap < pair._2.maxGlickoGap)
              (pair::pairs, true) // true - skip next as we already used it
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
          ask(instanceMasterProxy, ("requestServer", List(leftReq.userInfo, rightReq.userInfo)))
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
      // clean up
    queue = queue.filterNot(p => matches.exists(
      m => m._1.userInfo.steamId == p._1 || m._2.userInfo.steamId == p._1))
  }
}
