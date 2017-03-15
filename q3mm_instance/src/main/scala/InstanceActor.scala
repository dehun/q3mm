import QLServer.Endpoints
import akka.actor.Actor.Receive
import akka.actor.SupervisorStrategy._
import akka.actor._
import akka.event.Logging
import akka.util.Timeout
import akka.pattern.ask
import controllers.SteamUserInfo

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

class InstanceActor extends Actor {
  private val log = Logging(context.system, this)

  override val supervisorStrategy = OneForOneStrategy(
    loggingEnabled = true,
    maxNrOfRetries = 1, withinTimeRange = 1 minute) {
    case _ => akka.actor.SupervisorStrategy.Stop
  }

  private val masterUri = context.system.settings.config.getString("q3mm.instanceMasterUri")
  private implicit val timeout = Timeout(5 seconds)
  private val maxServers = context.system.settings.config.getInt("q3mm.maxServers")
  private var servers = Map.empty[Int, ActorRef]

  private val instanceMasterProxy = Await.result(context.actorSelection(masterUri).resolveOne(), timeout.duration)

  @scala.throws[Exception](classOf[Exception])
  override def preStart(): Unit = connectToMaster()

  private def connectToMaster():Unit = {
    implicit val timeout = Timeout(10 seconds)
    implicit val executionContext = context.system.dispatcher

    ask(instanceMasterProxy, ("i_wanna_be_your_dog", self, maxServers)).onComplete({
      case Success("good_doggie") =>
        log.info("waf waf")
      case Failure(ex) =>
        log.error(ex, "fatality, can not connect to master, retrying")
        connectToMaster()
      case _ => ???
    })
  }

  override def receive: Receive = {
    case ("requestServer", owners:List[SteamUserInfo]) =>
      if (servers.size >= maxServers) {
        log.warning("failing server creation request: overpopulated")
        instanceMasterProxy ! "freeSlot"
        sender() ! ("failed", "overpopulated")
      } else {
        val serverIndex = if (servers.isEmpty) 0 else List(servers.keys.min - 1, servers.keys.max + 1).filter(_ > 0).min
        log.info(s"request for server, lets spawn one more with idx ${serverIndex}!")
        val endpoints = Endpoints.random(context.system.settings.config.getString("q3mm.instanceInterface"), serverIndex)
        // spawn server
        val server = context.actorOf(Props(new QLWatchedServer(endpoints, serverIndex, owners)))
        context.watch(server)
        servers = servers.updated(serverIndex, server)
        sender() ! ("created", endpoints.url)
      }

    case Terminated(deadOne:ActorRef) =>
      log.warning(s"$deadOne terminated")
      servers.find(_._2 == deadOne).foreach(server => {
        log.info(s"server ${server._1} exited. freeing slot")
        servers -= server._1
        instanceMasterProxy ! "freeSlot"
      })

    case request@("findUser", steamId:String) =>
      import context.dispatcher
      //log.debug(s"searching for user ${steamId} for sender ${sender()}")
      val res = Try(Await.result(Future.sequence(servers.values.map(s => ask(s, request))), 10 seconds))
      res match {
        case Success(results) =>
          //log.debug(s"finished search for $steamId")
          sender() ! results.find(_ == ("foundUser", steamId)).getOrElse(("userNotFound", steamId))
        case Failure(ex) =>
          //log.debug(s"failed during user ${steamId} search, assuming there are no such user")
          sender() ! ("userNotFound", steamId)
      }
  }

  @scala.throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    servers = Map.empty
  }
}
