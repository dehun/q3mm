import akka.actor._

import scala.concurrent.duration._
import controllers.SteamUserInfo

class QueueActor extends Actor {
  private case class GameRequest(userInfo:SteamUserInfo, requester:ActorRef)
  private case class Tick()

  import context.dispatcher
  private val tick = context.system.scheduler.schedule(5 seconds, 5 seconds, self, Tick())

  private var queue = Map.empty[String, GameRequest]

  override def receive: Receive = {
    case ("enqueue", steamUserInfo:SteamUserInfo) =>
      Console.println("enqueuing", steamUserInfo)
      queue += ((steamUserInfo.steamId, GameRequest(steamUserInfo, sender())))

    case Tick() => {
      queue.values.grouped(2).map(xs => (xs.head, xs.last)).foreach(
        { case (leftReq, rightReq) =>
          Console.println(s"matched ${leftReq.userInfo} with ${rightReq.userInfo}")
          queue -= leftReq.userInfo.steamId
          queue -= rightReq.userInfo.steamId
          val challenge = ("challenge", "localhost")
          leftReq.requester ! challenge
          rightReq.requester ! challenge
        }
      )
    }
  }
}
