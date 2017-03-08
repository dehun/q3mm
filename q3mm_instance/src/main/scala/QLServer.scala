import java.io.File
import java.nio.file.{Files, Path, Paths}
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

import akka.actor.{Actor, ActorContext, ActorRef, ActorSystem, PoisonPill, Props}
import akka.actor.Actor.Receive
import akka.event.Logging
import controllers.SteamUserInfo

import scala.sys.process._
import scala.util.Random

object QLServer {
  case class Endpoints(interface:String, gamePort:Int, rconPort:Int, rconPassword:String, statsPassword:String, gamePassword:String) {
    val url:String = s"steam://connect/$interface:$gamePort/$gamePassword"
  }

  object Endpoints {
    def random(interface:String, serverIndex:Int) = Endpoints(
      interface,
      27960 + serverIndex,
      1024 + Random.nextInt(32768),
      Random.alphanumeric.take(10).mkString,
      Random.alphanumeric.take(10).mkString,
      Random.alphanumeric.take(10).mkString)
  }

  def spawn(context: ActorContext, endpoints: Endpoints,
            owners:List[SteamUserInfo]):ActorRef = {
    // prepare dirs
    val cwd = new File(context.system.settings.config.getString("q3mm.qlServerDir"))
    val fspath = new File(cwd.getPath.concat(s"/${endpoints.gamePort.toString}"))
    new File(fspath, "baseq3") mkdirs()
    Files.copy(
      Paths.get(cwd.getPath.concat("/duel.txt")),
      Paths.get(fspath.getPath.concat("/baseq3/duel.txt")), REPLACE_EXISTING)
    // spawn
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
      "+set", "g_password", s"${endpoints.gamePassword}",
      "+set", "g_voteFlags", "0",
      "+set", "g_dropInactive", "1",
      "+set", "g_inactivity", "60",
      "+set", "g_allowVoteMidGame", "0",
      "+set", "g_allowSpecVote", "0",
      "+set", "sv_master", "0",
      "+set", "fs_homepath", s"${cwd.toPath.toString}/${endpoints.gamePort}",
      "+set", "sv_mapPoolFile", "duel.txt",
      "+set", "serverstartup", "startRandomMap")
    log.info(s"server cmdline is ${cmdLine}")
    val proc = Process(cmdLine, cwd).run()
    context.actorOf(Props(new QLServer(proc, endpoints, owners)))
  }
}

class QLServer(process:Process, val endpoints:QLServer.Endpoints, owners:List[SteamUserInfo]) extends Actor {
  val log = Logging(context.system, this)
  val processWatchdog = new Thread(new Runnable {
    override def run() = {
      log.info(s"watching process ${process}")
      val exitValue = process.exitValue()
      log.info(s"ql server exited with code ${exitValue}, terminating responsible actor")
      self ! PoisonPill
    }
  })
  processWatchdog.start()

  override def receive: Receive = {
    case ("findUser", steamId) =>
      if (owners.exists(_.steamId == steamId))
        sender() ! ("foundUser", steamId)
      else
        sender() ! ("userNotFound", steamId)
  }

  override def postStop(): Unit = {
    log.info(s"actor death, killing process $process")
    process.destroy()
  }
}
