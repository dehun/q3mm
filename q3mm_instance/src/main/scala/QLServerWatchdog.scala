import QLServer.Endpoints
import akka.actor.Actor.Receive
import akka.actor.{ActorRef, PoisonPill}
import akka.event.Logging
import akka.util.Timeout
import akka.actor._

import scala.concurrent.duration._
import scala.util.Try
import play.api.libs.json
import play.api.libs.json.{JsValue, Json}

class QLServerWatchdog(endpoints: Endpoints, server:ActorRef, serverIndex:Int) extends QLStatsMonitorActor(endpoints) {
  private val log = Logging(context.system, this)

  override val supervisorStrategy = AllForOneStrategy(
    loggingEnabled=true, maxNrOfRetries = 1, withinTimeRange = 1 minute) {
    case _ => akka.actor.SupervisorStrategy.Stop
  }

  import context._
  context.watch(server)
  context.system.scheduler.scheduleOnce(120 seconds) { self ! "connect_timeout_check" }
  context.system.scheduler.scheduleOnce(360 seconds) { self ! "match_start_check" }

  private var playersCount = 0
  private var matchStarted = false

  override def receive: Receive = {
    case ("stats_event", stats_event_str:String) =>
      Try(Json.parse(stats_event_str)).foreach(handle_stats_event)

    case "connect_timeout_check" =>
      if (playersCount < 2) {
        log.warning("less than 2 players, kill the server")
        server ! PoisonPill
      }

    case "match_start_check" =>
      if (!matchStarted) {
        log.warning("match has not started, kill the server")
        server ! PoisonPill
      }

    case Terminated(server) =>
      log.warning("server is dead, dying")
      self ! Kill

    case _ => {
      log.error("unknown message received")
      ???
    }
  }

  private def handle_stats_event(stats_event:JsValue):Unit = {
    log.warning(s"stats_event ${stats_event}")
    (stats_event \ "TYPE").as[String] match {
      case "PLAYER_DISCONNECT" =>
        log.info("player disconnected, just die")
        self ! PoisonPill
      case "PLAYER_SWITCHTEAM" =>
        if ((stats_event \ "DATA" \ "KILLER"\ "TEAM").as[String] =="FREE") {
          playersCount += 1
          log.info(s"player joined the match, now we have ${playersCount} players")
        }
      case "MATCH_STARTED" =>
        matchStarted = true
      case evt =>
        log.info(s"received event ${evt}")
    }
  }

  @scala.throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    log.warning("watchdog is dead now")
  }
}
