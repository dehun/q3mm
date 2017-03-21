import akka.actor.Actor.Receive
import akka.event.Logging
import akka.actor._
import akka.pattern.ask
import controllers.{InstanceMasterStats, SteamUserInfo}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Random, Success, Try}

class InstanceMasterActor extends Actor {
  case class WorkerMetaInfo(potential:Int, maxPotential:Int)

  private val log = Logging(context.system, this)
  private var slaves = Map.empty[ActorRef, WorkerMetaInfo]

  private def areUsersRegistered(users:List[String]): Future[Boolean] = {
    import context.dispatcher
    log.debug(s"checking are users ${users} already registered in system")
    Future.find(slaves.keys.flatMap(s => users.map(owner => ask(s, ("findUser", owner))(10 seconds)))) {
      case req@("foundUser", foundUser) if users.contains(foundUser) =>
        log.info(s"found user ${foundUser}")
        true
      case _ => false
    }.map(_.isDefined)
  }

  override def receive: Receive = {
    case ("i_wanna_be_your_dog", dog:ActorRef, potential:Int) =>
      log.info(s"got new slave ${sender()}")
      slaves = slaves.updated(dog, WorkerMetaInfo(potential, potential))
      context.watch(dog)
      sender() ! "good_doggie"

    case Terminated(deadOne) if slaves.contains(deadOne) =>
      log.warning(s"slave $deadOne is dead")
      slaves -= deadOne

    case request@("requestServer", owners:List[SteamUserInfo]) =>
      implicit val executionContext = context.dispatcher
      val requestor = sender()
      areUsersRegistered(owners.map(_.steamId)).onComplete({
        case Success(true) =>
          log.warning("user already in game")
          requestor ! ("failed", "already in game")
        case Failure(ex) =>
          log.warning(s"during user registration check happened $ex")
          requestor ! ("failed", "failued to query user existance")
        case Success(false) =>
          log.info(s"users are not registered, lets create server for them ${owners}")
          val randomSlave = slaves.find(_._2.potential > 0).map(_._1)
          if (randomSlave.isDefined) {
            log.info(s"forwarding creation request to $randomSlave")
            val slaveMeta = slaves(randomSlave.get)
            slaves = slaves.updated(randomSlave.get, WorkerMetaInfo(slaveMeta.potential - 1, slaveMeta.maxPotential))
            randomSlave.get.tell(request, requestor)
          } else {
            log.error(s"no slave to satisfy creation request")
            requestor ! ("failed", "not enough slaves")
          }
      })

    case "freeSlot" =>
      val newPotential = slaves(sender()).potential + 1
      log.info(s"updating worker ${sender()} potential to $newPotential")
      slaves = slaves.updated(sender(), slaves(sender).copy(potential = newPotential))

    case "stats" =>
      log.info("gathering backend stats")
      val maxServers = slaves.values.map(_.maxPotential).sum
      val leftServers = slaves.values.map(_.potential).sum
      sender() ! InstanceMasterStats(maxServers - leftServers, maxServers)
  }
}
