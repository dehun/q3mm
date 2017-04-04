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

  private var instanceMasterProxy = Option.empty[ActorRef]
  self ! "connectToMaster"

  private def connectToMaster():Unit = {
    implicit val timeout = Timeout(10 seconds)
    implicit val executionContext = context.system.dispatcher
    if (instanceMasterProxy.isDefined) return

    for {nm <- Try(Await.result(context.actorSelection(masterUri).resolveOne(), timeout.duration))
         ans <- ask(nm, ("i_wanna_be_your_dog", self, maxServers))} {
      ans match {
        case "good_doggie" =>
          instanceMasterProxy = Some(nm)
          log.info("waf waf")
          instanceMasterProxy.foreach(context.watch)
      }
    }
    if (instanceMasterProxy.isEmpty) {
      log.error("fatality, can not connect to master, retrying")
      context.system.scheduler.scheduleOnce(5 seconds, self, "connectToMaster")
    }
  }


  override def receive: Receive = {
    case "connectToMaster" =>
      connectToMaster()

    case ("requestServer", owners: List[SteamUserInfo], isPrivate:Boolean, glicko:Int) =>
      if (servers.size >= maxServers) {
        log.warning("failing server creation request: overpopulated")
        instanceMasterProxy.foreach(_ ! "freeSlot")
        sender() ! ("failed", "overpopulated")
      } else {
        val serverIndex = if (servers.isEmpty) 0 else List(servers.keys.min - 1, servers.keys.max + 1).filter(_ > 0).min
        log.info(s"request for server, lets spawn one more with idx ${serverIndex}!")
        val endpoints = Endpoints.random(
          context.system.settings.config.getString("q3mm.instanceInterface"),
          context.system.settings.config.getString("q3mm.statsPassword"),
          serverIndex, isPrivate=isPrivate, glicko)
        // spawn server
        val server = context.actorOf(Props(new QLWatchedServer(endpoints, serverIndex, owners)))
        context.watch(server)
        servers = servers.updated(serverIndex, server)
        sender() ! ("created", endpoints.url)
      }

    case Terminated(deadOne) if instanceMasterProxy.contains(deadOne) =>
      instanceMasterProxy = None
      connectToMaster()

    case Terminated(deadOne: ActorRef) =>
      log.warning(s"$deadOne terminated")
      servers.find(_._2 == deadOne).foreach(server => {
        log.info(s"server ${server._1} exited. freeing slot")
        servers -= server._1
        instanceMasterProxy.foreach(_ ! "freeSlot")
      })

    case request@("findUser", steamId:String) =>
      import context.dispatcher
      log.debug(s"searching for the user ${steamId}")
      val requestor = sender()
      val findFuture = Future.find(servers.values.map(s => ask(s, request))) {
        case ("foundUser", foundSteamId, _) => foundSteamId == steamId
        case _ => false
      }.onComplete({
        case Success(Some(result)) =>
          requestor ! result
        case Success(None) =>
          requestor ! ("userNotFound", steamId)
        case Failure(ex) =>
          requestor ! ("userNotFound", steamId)
      })
  }

  @scala.throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    log.warning("instance actor is dead")
    servers = Map.empty
  }
}
