import akka.actor.Actor.Receive
import akka.event.Logging
import akka.actor._

import scala.util.Random

class InstanceMasterActor extends Actor {

  case class WorkerMetaInfo(potential:Int)

  private val log = Logging(context.system, this)
  private var workers = Map.empty[ActorRef, WorkerMetaInfo]

  override def receive: Receive = {
    case ("i_wanna_be_your_dog", dog:ActorRef, potential:Int) =>
      log.info(s"got new slave ${sender()}")
      workers = workers.updated(dog, WorkerMetaInfo(potential))
      context.watch(dog)
      sender() ! "good_doggie"

    case Terminated(deadOne) =>
      log.warning(s"slave $deadOne is dead")
      workers -= deadOne

    case request@("requestServer", _, _) =>
      if (workers.nonEmpty) {
        val randomSlave = workers.filter(_._2.potential > 0).toVector(Random.nextInt(workers.size))._1
        log.info(s"forwarding creation request to $randomSlave")
        randomSlave.forward(request)
        workers = workers.updated(randomSlave, WorkerMetaInfo(workers.get(randomSlave).get.potential - 1))
      } else {
        log.error(s"no slave to satisfy creation request")
        sender() ! ("failed", "not enough slaves")
      }
    case ("updateInfo", potential:Int) =>
      log.info(s"updating worker ${sender()} potential to ${potential}")
      workers = workers.updated(sender(), WorkerMetaInfo(potential))
  }
}
