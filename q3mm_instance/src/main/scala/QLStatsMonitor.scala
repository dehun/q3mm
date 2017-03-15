import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.Actor.Receive
import akka.actor._
import akka.event.Logging
import scala.concurrent.duration._
import org.zeromq.{ZMQ, ZMQException}


//TODO: move to separate library
class ZmqSubSocketActor(endpoint:String, port:Int,
                        userName:String, password:String,
                        msgPrefix:String,
                        receiver:ActorRef) extends Actor {
  private val log = Logging(context.system, this)

  context.watch(receiver)

  // init zmq socket
  private val zmqcontext = ZMQ.context(1)
  private val zmqsocket = zmqcontext.socket(ZMQ.SUB)

  zmqsocket.setReceiveTimeOut(0)
  zmqsocket.setPlainUsername(userName.getBytes())
  zmqsocket.setPlainPassword(password.getBytes())
  zmqsocket.connect(s"tcp://${endpoint}:${port}")
  zmqsocket.subscribe("".getBytes())

  self ! "poll"

  override def receive: Receive = {
    case "poll" =>
      try {
        val buf = zmqsocket.recvStr(Charset.defaultCharset())
        if (buf != null) {
          log.debug(s"QLStatsMonitor received stats event ${buf}")
          receiver ! (msgPrefix, buf)
          self ! "poll"
        } else  {
          import context.dispatcher
          context.system.scheduler.scheduleOnce(1 seconds, self, "poll")
        }
      } catch {
        case ex:ZMQException => log.error(s"got zmq exception during polling $endpoint:$port, $ex")
      }

    case Terminated(receiver) => context.stop(self)
  }

  @scala.throws[Exception](classOf[Exception])
  override def postStop(): Unit = {
    log.info("closing zmq socket")
    zmqsocket.close()
    zmqcontext.term()
    super.postStop()
  }
}

abstract class QLStatsMonitorActor(endpoints:QLServer.Endpoints) extends Actor {
  override val supervisorStrategy = AllForOneStrategy(maxNrOfRetries = 1, withinTimeRange = 1 minute) {
    case _ => akka.actor.SupervisorStrategy.Escalate
  }

  private val log = Logging(context.system, this)
  private val zmqSocketActor = context.actorOf(Props(
    new ZmqSubSocketActor(endpoints.interface, endpoints.gamePort, "stats",
      endpoints.statsPassword, "stats_event", self)))
}
