import java.nio.charset.Charset

import akka.actor.Actor.Receive
import akka.actor._
import akka.event.Logging
import org.zeromq.{ZMQ, ZMQException}


//TODO: move to separate library
abstract class QLStatsMonitorActor(endpoints:QLServer.Endpoints) extends Actor {
  private val log = Logging(context.system, this)
  private var stopping:Boolean = false

  private val zmqcontext = ZMQ.context(1)
  private val zmqsocket = zmqcontext.socket(ZMQ.SUB)

  private val workThread = new Thread(new Runnable {
    override def run() = worker
  })
  workThread.start()

  def worker = {
    try {
      zmqsocket.setPlainUsername("stats".getBytes())
      zmqsocket.setPlainPassword(endpoints.statsPassword.getBytes())
      zmqsocket.connect(s"tcp://${endpoints.interface}:${endpoints.gamePort}")
      zmqsocket.subscribe("".getBytes())
      while (!stopping) {
        val buf = zmqsocket.recvStr(Charset.defaultCharset())
        log.info(s"QLStatsMonitor received stats event ${buf}")
        context.self ! ("stats_event", buf)
      }
    } catch {
      case ex: ZMQException =>
        log.error("error in QLStatsMonitor ${ex.toString}")
        onFailure()
    } finally {
      zmqsocket.close()
      zmqcontext.term()
    }
  }

  def onFailure():Unit

  @scala.throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    stopping = true
    super.postStop()
  }
}
