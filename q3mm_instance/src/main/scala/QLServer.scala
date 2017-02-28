import java.io.File

import akka.actor.{Actor, ActorContext, ActorRef, ActorSystem, Props}
import akka.actor.Actor.Receive
import controllers.SteamUserInfo

import scala.sys.process._
import scala.util.Random

object QLServer {
  case class Endpoints(interface:String, gamePort:Int, rconPort:Int, rconPassword:String, statsPassword:String) {
    val url:String = s"steam://connect/$interface:$gamePort" // TODO: password!
  }
  object Endpoints {
    def random(interface:String) = Endpoints(
      interface,
      1024 + Random.nextInt(32768),
      1024 + Random.nextInt(32768),
      Random.alphanumeric.take(10).mkString,
      Random.alphanumeric.take(10).mkString)
  }

  def spawn(context: ActorContext, endpoints: Endpoints,
            leftUser:SteamUserInfo, rightUser:SteamUserInfo):ActorRef = {
    val cwd = new File("/home/steam/.steam/steamcmd/steamapps/common/qlds")
    val cmdLine = s"""exec ./run_server_x86.sh \
                     |+set net_strict 1 \
                     |+set sv_lan 1
                     |+set net_port ${endpoints.gamePort} \
                     |+set sv_hostname "q3mm server" \
                     |+set zmq_rcon_enable 1 \
                     |+set zmq_rcon_password ${endpoints.rconPassword} \
                     |+set zmq_rcon_port ${endpoints.rconPort} \
                     |+set zmq_stats_enable 1 \
                     |+set zmq_stats_password ${endpoints.statsPassword} \
                     |+set zmq_stats_port ${endpoints.gamePort}""".stripMargin

    val proc = Process(cmdLine, cwd).run()
    context.actorOf(Props(new QLServer(proc, endpoints, leftUser, rightUser)))
  }
}

class QLServer(process:Process, val endpoints:QLServer.Endpoints, leftUser:SteamUserInfo, rightUser:SteamUserInfo) extends Actor {
  override def receive: Receive = ???

  override def postStop(): Unit = process.destroy()
}
