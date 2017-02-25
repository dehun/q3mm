import java.io.File

import controllers.SteamUserInfo

import scala.sys.process._
import scala.util.Random

object QLServer {
  case class Endpoints(gamePort:Int, rconPort:Int, statsPort:Int)
  object Endpoints {
    def random() = Endpoints(1024 + Random.nextInt(32768), 1024 + Random.nextInt(32768), 1024 + Random.nextInt(32768))
  }

  def spawn(leftUser:SteamUserInfo, rightUser:SteamUserInfo):QLServer = {
    val cwd = new File("/home/steam/.steam/steamcmd/steamapps/common/qlds")
    val cmdLine = "/home/steam/.steam/steamcmd/steamapps/common/qlds/q3ds.sh"
    val endpoints = Endpoints.random()
    val proc = Process(cmdLine, cwd,
      "gamePort" -> endpoints.gamePort.toString,
      "rconPort" -> endpoints.rconPort.toString,
      "statsPort" -> endpoints.statsPort.toString).run()
    new QLServer(proc, endpoints, leftUser, rightUser)
  }
}

class QLServer(process:Process, endpoints:QLServer.Endpoints, leftUser:SteamUserInfo, rightUser:SteamUserInfo) {
  val url = s"steam://connect/127.0.0.1:${endpoints.gamePort}"
}
