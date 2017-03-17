import akka.actor.Actor.Receive
import akka.event.Logging
import akka.actor._
import akka.pattern.ask
import controllers.SteamUserInfo

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Random, Success, Try}

class InstanceMasterActor extends Actor {
  case class WorkerMetaInfo(potential:Int)

  private val log = Logging(context.system, this)
  private var slaves = Map.empty[ActorRef, WorkerMetaInfo]

  private def areUsersRegistered(users:List[String]): Future[Boolean] = {
    import context.dispatcher
    log.debug(s"checking are users ${users} already registered in system")
    Future.sequence(slaves.keys.flatMap(s => users.map(owner => ask(s, ("findUser", owner))(10 seconds)))).map(
      results => {
        log.debug(s"search got results ${results}")
        results.map(_.asInstanceOf[(String, String)]).filter(_._1 == "foundUser").exists(r => users.contains(r._2))
      }
    )
  }

  override def receive: Receive = {
    case ("i_wanna_be_your_dog", dog:ActorRef, potential:Int) =>
      log.info(s"got new slave ${sender()}")
      slaves = slaves.updated(dog, WorkerMetaInfo(potential))
      context.watch(dog)
      sender() ! "good_doggie"

    case Terminated(deadOne) if slaves.contains(deadOne) =>
      log.warning(s"slave $deadOne is dead")
      slaves -= deadOne

    case request@("requestServer", owners:List[SteamUserInfo]) =>
      implicit val executionContext = context.dispatcher
      val isAlreadyOwning = Try(Await.result(areUsersRegistered(owners.map(_.steamId)), 10 seconds))
      isAlreadyOwning match {
        case Success(true) =>
          log.warning("user already in game")
          sender() ! ("failed", "already in game")
        case Failure(ex) =>
          log.warning(s"during user registration check happened $ex")
          sender() ! ("failed", "failued to query user existance")
        case Success(false) =>
          log.info(s"users are not registered, lets create server for them ${owners}")
          val randomSlave = slaves.find(_._2.potential > 0).map(_._1)
          if (randomSlave.isDefined) {
            log.info(s"forwarding creation request to $randomSlave")
            slaves = slaves.updated(randomSlave.get, WorkerMetaInfo(slaves(randomSlave.get).potential - 1))
            randomSlave.get.forward(request)
          } else {
            log.error(s"no slave to satisfy creation request")
            sender() ! ("failed", "not enough slaves")
          }
      }

    case "freeSlot" =>
      val newPotential = slaves(sender()).potential + 1
      log.info(s"updating worker ${sender()} potential to $newPotential")
      slaves = slaves.updated(sender(), WorkerMetaInfo(newPotential))
  }
}
