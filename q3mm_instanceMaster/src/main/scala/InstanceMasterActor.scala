import akka.actor.Actor.Receive
import akka.event.Logging
import akka.actor._

import scala.util.Random

class InstanceMasterActor extends Actor {
  private val log = Logging(context.system, this)
  private var workers = Set.empty[ActorRef]

  override def receive: Receive = {
    case "i_wanna_be_your_dog" =>
      log.info(s"got new slave ${sender()}")
      workers += sender()
      context.watch(sender())
      sender() ! "good_doggie"

    case Terminated(deadOne) =>
      log.warning(s"slave $deadOne is dead")
      workers -= deadOne

    case request@("requestServer", _, _) =>
      // TODO: normal load balancing? Router?
      if (workers.nonEmpty) {
        val randomSlave = workers.toVector(Random.nextInt(workers.size))
        log.info(s"forwarding creation request to $randomSlave")
        randomSlave.forward(request)
      } else {
        log.error(s"no slave to satisfy creation request")
        sender() ! "failed"
      }
  }
}
