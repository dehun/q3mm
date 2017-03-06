import QLServer.Endpoints
import akka.actor.Actor.Receive
import akka.actor.{ActorRef, PoisonPill}
import akka.event.Logging

import scala.util.parsing.json.JSON

class QLServerWatchdog(endpoints: Endpoints, server:ActorRef, serverIndex:Int, supervisor:ActorRef) extends QLStatsMonitorActor(endpoints) {
  private val log = Logging(context.system, this)

  override def receive: Receive = {
    case ("stats_event", stats_event:String) =>
      handle_stats_event(stats_event)
  }

  private def handle_stats_event(stats_event:String):Unit = {
    log.warning(s"stats_event ${stats_event}")
  }

  private def terminateServer() = server ! PoisonPill

  override def onFailure(): Unit = {
    terminateServer()
    supervisor ! ("serverExit", "died", serverIndex)
  }

  @scala.throws[Exception](classOf[Exception]) override
  def postStop(): Unit = terminateServer()
}
