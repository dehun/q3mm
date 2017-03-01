import QLServer.Endpoints
import akka.actor.Actor.Receive
import akka.actor.{ActorRef, PoisonPill}

import scala.util.parsing.json.JSON

class QLServerWatchdog(endpoints: Endpoints, server:ActorRef) extends QLStatsMonitorActor(endpoints) {
  override def receive: Receive = {
    case ("stats_event", stats_event:String) =>
      handle_stats_event(stats_event)
  }

  private def handle_stats_event(stats_event:String):Unit = ??? // TODO: implement me! at the moment not needed

  private def terminateServer() = server ! PoisonPill

  override def onFailure(): Unit = terminateServer()
}
