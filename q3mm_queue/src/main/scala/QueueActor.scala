import akka.actor._
import akka.event.Logging
import akka.pattern.ask
import akka.util.Timeout

import scala.util._
import scala.concurrent.duration._
import controllers.SteamUserInfo

class QueueActor extends Actor {
  private val log = Logging(context.system, this)
  private val instanceCreationTimeout = 360 seconds
  private val remoteInstanceMasterPath = "akka.tcp://q3mm@127.0.0.1:2560/user/instanceMaster"
  private val instanceMasterProxy = context.actorSelection(remoteInstanceMasterPath)

  private case class GameRequest(userInfo:SteamUserInfo, requester:ActorRef)
  private case class Tick()

  import context.dispatcher
  private val _tick = context.system.scheduler.schedule(5 seconds, 5 seconds, self, Tick())

  private var queue = Map.empty[String, GameRequest]

  override def receive: Receive = {
    case ("enqueue", steamUserInfo:SteamUserInfo) =>
      log.info(s"enqueuing ${steamUserInfo}")
      queue += ((steamUserInfo.steamId, GameRequest(steamUserInfo, sender())))

    case ("dequeue", steamId:String) =>
      log.info(s"dequeuing $steamId")
      queue -= steamId

    case Tick() => {
      queue.values.grouped(2).map(xs => (xs.head, xs.last)).foreach( // TODO: .filter(_.size == 2)!
        { case (leftReq, rightReq) =>
          log.info(s"matched ${leftReq.userInfo.steamId} with ${rightReq.userInfo.steamId}")
          queue -= leftReq.userInfo.steamId
          queue -= rightReq.userInfo.steamId
          implicit val timeout = Timeout(instanceCreationTimeout)
          ask(instanceMasterProxy, ("requestServer", leftReq.userInfo, rightReq.userInfo))
            .recover({case ex => Seq(leftReq.requester, rightReq.requester).foreach(_ ! ("failed", ex))})
            .foreach({
              case (("created", serverAddress)) =>
                val challenge = ("challenge", serverAddress)
                Seq(leftReq.requester, rightReq.requester).foreach(_ ! challenge)
              case (("failed", error)) =>
                Seq(leftReq.requester, rightReq.requester).foreach(_ ! ("failed", error))
            })
        }
      )
    }
  }
}
