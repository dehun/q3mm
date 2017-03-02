import QLServer.Endpoints
import akka.actor.Actor.Receive
import akka.actor._
import akka.event.Logging
import akka.util.Timeout
import controllers.SteamUserInfo

import scala.concurrent.Future
import scala.concurrent.duration._

class InstanceActor extends Actor {
  private val log = Logging(context.system, this)
  private val masterUri = context.system.settings.config.getString("q3mm.instanceMasterUri")
  private implicit val timeout = Timeout(5 seconds)
  private var servers = Set.empty[(ActorRef, ActorRef)]

  @scala.throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    val instanceMasterProxy = context.actorSelection(masterUri)
    instanceMasterProxy ! "i_wanna_be_your_dog"
  }

  override def receive: Receive = {
    case ("requestServer", leftUser:SteamUserInfo, rightUser:SteamUserInfo) =>
      if (servers.size >= context.system.settings.config.getInt("q3mm.maxServers")) {
        sender() ! ("failed", "overpopulated")
      } else {
        log.info(s"request for server, lets spawn one more to ${servers.size}!")
        val endpoints = Endpoints.random(context.system.settings.config.getString("q3mm.instanceInterface"), servers.size)
        // spawn server
        val server = QLServer.spawn(context, endpoints, leftUser, rightUser)
        // spawn watchdog
        val watchdog = context.actorOf(Props(new QLServerWatchdog(endpoints, server)))
        //
        servers += ((server, watchdog))
        sender() ! ("created", endpoints.url)
      }

    case ("good_doggie") =>
      log.info("waf waf")
  }

  @scala.throws[Exception](classOf[Exception])
  override def postStop(): Unit = servers.foreach(_._2 ! PoisonPill)
}
