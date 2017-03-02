import java.io.File

import akka.actor.{Actor, ActorContext, ActorRef, ActorSystem, Props}
import akka.actor.Actor.Receive
import akka.event.Logging
import controllers.SteamUserInfo

import scala.sys.process._
import scala.util.Random

object QLServer {
  case class Endpoints(interface:String, gamePort:Int, rconPort:Int, rconPassword:String, statsPassword:String) {
    val url:String = s"steam://connect/$interface:$gamePort"
  }
  object Endpoints {
    def random(interface:String, serverIndex:Int) = Endpoints(
      interface,
      27960 + serverIndex,
      1024 + Random.nextInt(32768),
      Random.alphanumeric.take(10).mkString,
      Random.alphanumeric.take(10).mkString)
  }

  def spawn(context: ActorContext, endpoints: Endpoints,
            leftUser:SteamUserInfo, rightUser:SteamUserInfo):ActorRef = {
    val cwd = new File(context.system.settings.config.getString("q3mm.qlServerDir"))
    val log = Logging(context.system, context.self)
    log.info(s"spawning new server! $endpoints")
    val cmdLine = Seq("./run_server_x86.sh",
      "+set", "net_strict", "1",
      "+set", "net_ip", s"${endpoints.interface}",
      "+set", "net_port", s"${endpoints.gamePort}",
      "+set", "sv_hostname", "q3mm server",
      "+set", "zmq_rcon_enable", "1",
      "+set", "zmq_rcon_password", s"${endpoints.rconPassword}",
      "+set", "zmq_rcon_port", s"${endpoints.rconPort}",
      "+set", "zmq_stats_enable", "1",
      "+set", "zmq_stats_password", s"${endpoints.statsPassword}",
      "+set", "zmq_stats_port", s"${endpoints.gamePort}",
      "+set", "g_voteFlags", "0",
      "+set", "g_dropInactive", "1",
      "+set", "g_inactivity", "60",
      "+set", "sv_mapPoolFile", "duel.txt",
      "+set", "serverstartup", "startRandomMap")
    log.info(s"server cmdline is ${cmdLine}")
    val proc = Process(cmdLine, cwd).run()
    context.actorOf(Props(new QLServer(proc, endpoints, leftUser, rightUser)))
  }
}

class QLServer(process:Process, val endpoints:QLServer.Endpoints, leftUser:SteamUserInfo, rightUser:SteamUserInfo) extends Actor {
  val log = Logging(context.system, this)

  override def receive: Receive = {
    case x => log.info(s"received ${x}, but should not...")
  }

  override def postStop(): Unit = process.destroy()
}
