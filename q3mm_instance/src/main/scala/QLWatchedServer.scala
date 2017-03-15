import QLServer.Endpoints
import akka.actor.{Actor, AllForOneStrategy, Kill, OneForOneStrategy, PoisonPill, Props, Terminated}
import akka.actor.Actor.Receive
import akka.event.Logging

import scala.concurrent.duration._
import controllers.SteamUserInfo

class QLWatchedServer(endpoints: Endpoints, serverIdx:Int, owners:List[SteamUserInfo]) extends Actor {
  private val log = Logging(context.system, this)
  override val supervisorStrategy = AllForOneStrategy(
    loggingEnabled=true, maxNrOfRetries=1, withinTimeRange=1 minute) {
    case _ => akka.actor.SupervisorStrategy.Stop
  }

  private val server = QLServer.spawn(context, endpoints, owners)
  private val watchdog = context.actorOf(Props(new QLServerWatchdog(endpoints, server, serverIdx)))

  context.watch(watchdog)

  override def receive: Receive = {
    case _:Terminated => {
      log.warning("terminated received, watchedserver killing itself")
      self ! Kill
    }
    case req@("findUser", steamId) => server.forward(req)
    case _ => {
      log.error("received unknown message")
      ???
    }
  }
}
