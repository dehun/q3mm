import QLServer.Endpoints
import akka.actor.Actor.Receive
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
  private val masterUri = context.system.settings.config.getString("q3mm.instanceMasterUri")
  private implicit val timeout = Timeout(5 seconds)
  private val maxServers = context.system.settings.config.getInt("q3mm.maxServers")
  private var servers = Map.empty[Int, (ActorRef, ActorRef)]

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
        val server = QLServer.spawn(context, endpoints, owners)
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
      instanceMasterProxy ! "freeSlot"

    case request@("findUser", steamId:String) =>
      import context.dispatcher
      log.info(s"searching for user ${steamId} for sender ${sender()}")
      val res = Try(Await.result(Future.sequence(servers.values.map(_._1).map(s => ask(s, request))), 10 seconds))
      res match {
        case Success(results) =>
          log.info("finished search")
          sender() ! results.find(_ == ("foundUser", steamId)).getOrElse(("userNotFound", steamId))
        case Failure(ex) =>
          log.info("failed during user search, assuming there are no such user")
          sender() ! ("userNotFound", steamId)
      }
  }

  @scala.throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    log.warning("post stop, killing all servers")
    servers.values.foreach(_._2 ! PoisonPill)
    servers = Map.empty
  }
}
