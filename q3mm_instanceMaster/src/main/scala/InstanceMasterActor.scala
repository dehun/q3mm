import akka.actor.Actor.Receive
import akka.event.Logging
import akka.actor._
import akka.pattern.ask
import controllers.SteamUserInfo

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.{Failure, Random, Success}

class InstanceMasterActor extends Actor {

  case class WorkerMetaInfo(potential:Int)

  private val log = Logging(context.system, this)
  private var workers = Map.empty[ActorRef, WorkerMetaInfo]

  private def areUsersRegistered(users:List[String]): Future[Boolean] = {
    import context.dispatcher
    Future.sequence(workers.keys.flatMap(s => users.map(owner => ask(s, ("findUser", owner))(10 seconds)))).map({
      case results:List[(String, String)]=> results.filter(_._1 == "foundUser").exists(r => users.contains(r._2))
    })
  }

  override def receive: Receive = {
    case ("i_wanna_be_your_dog", dog:ActorRef, potential:Int) =>
      log.info(s"got new slave ${sender()}")
      workers = workers.updated(dog, WorkerMetaInfo(potential))
      context.watch(dog)
      sender() ! "good_doggie"

    case Terminated(deadOne) =>
      log.warning(s"slave $deadOne is dead")
      workers -= deadOne

    case request@("requestServer", owners:List[SteamUserInfo]) =>
      implicit val executionContext = context.dispatcher
      areUsersRegistered(owners.map(_.steamId)).onComplete({
        case Success(true) => sender() ! ("failed", "already in game")
        case Failure(ex) =>
          log.warning(s"during user registration check happened and $ex")
          sender() ! ("failed", "failued to query user existance")
        case Success(false) =>
          if (workers.nonEmpty) {
            val randomSlave = workers.filter(_._2.potential > 0).toVector(Random.nextInt(workers.size))._1
            log.info(s"forwarding creation request to $randomSlave")
            randomSlave.forward(request)
            workers = workers.updated(randomSlave, WorkerMetaInfo(workers(randomSlave).potential - 1))
          } else {
            log.error(s"no slave to satisfy creation request")
            sender() ! ("failed", "not enough slaves")
          }
      })

    case ("updateInfo", potential:Int) =>
      log.info(s"updating worker ${sender()} potential to $potential")
      workers = workers.updated(sender(), WorkerMetaInfo(potential))
  }
}
