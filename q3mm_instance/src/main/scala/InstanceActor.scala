import QLServer.Endpoints
import akka.actor.Actor.Receive
import akka.actor._
import akka.event.Logging
import akka.util.Timeout
import akka.pattern.ask
import controllers.SteamUserInfo

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class InstanceActor extends Actor {
  private val log = Logging(context.system, this)
  private val masterUri = context.system.settings.config.getString("q3mm.instanceMasterUri")
  private implicit val timeout = Timeout(5 seconds)
  private var servers = Map.empty[Int, (ActorRef, ActorRef)]

  @scala.throws[Exception](classOf[Exception])
  override def preStart(): Unit = connectToMaster()

  private def connectToMaster():Unit = {
    implicit val timeout = Timeout(10 seconds)
    implicit val executionContext = context.system.dispatcher
    val instanceMasterProxy = context.actorSelection(masterUri)
    ask(instanceMasterProxy, "i_wanna_be_your_dog").onComplete({
      case Success("good_doggie") =>
        log.info("waf waf")
      case Failure(ex) =>
        log.error(ex, "fatality, can not connect to master, retrying")
        connectToMaster()
      case _ => ???
    })
  }

  override def receive: Receive = {
    case ("requestServer", leftUser:SteamUserInfo, rightUser:SteamUserInfo) =>
      if (servers.size >= context.system.settings.config.getInt("q3mm.maxServers")) {
        log.warning("failing server creation request: overpopulated")
        sender() ! ("failed", "overpopulated")
      } else {
        val serverIndex = if (servers.isEmpty) 0 else List(servers.keys.min - 1, servers.keys.max + 1).filter(_ > 0).min
        log.info(s"request for server, lets spawn one more with idx ${serverIndex}!")
        val endpoints = Endpoints.random(context.system.settings.config.getString("q3mm.instanceInterface"), serverIndex)
        // spawn server
        val server = QLServer.spawn(context, endpoints, leftUser, rightUser)
        // spawn watchdog
        val watchdog = context.actorOf(Props(new QLServerWatchdog(endpoints, server, serverIndex, self)))
        //
        assert(servers.get(serverIndex).isEmpty)
        servers = servers.updated(serverIndex, (server, watchdog))
        sender() ! ("created", endpoints.url)
      }

    case ("serverExit", reason, idx:Int) =>
      log.info(s"server ${idx} exited with reason ${reason}")
      servers -= idx
  }

  @scala.throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    log.warning("post stop, killing all servers")
    servers.values.foreach(_._2 ! PoisonPill)
    servers = Map.empty
  }
}
