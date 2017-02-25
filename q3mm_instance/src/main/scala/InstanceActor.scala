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

  private var servers = List.empty[QLServer]

  @scala.throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    val instanceMasterProxy = context.actorSelection(masterUri)
    instanceMasterProxy ! "i_wanna_be_your_dog"
  }

  override def receive: Receive = {
    case ("requestServer", leftUser:SteamUserInfo, rightUser:SteamUserInfo) =>
      log.info("request for server!")
      val server = QLServer.spawn(leftUser, rightUser)
      servers = server::servers
      sender() ! ("created", server.url)

    case ("good_doggie") =>
      log.info("waf waf")
  }
}
