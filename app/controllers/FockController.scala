package controllers

import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.event.Logging
import akka.stream.Materializer
import akka.util.Timeout
import com.google.inject.Inject
import play.api.mvc.{Action, Controller}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Random, Success}
import akka.pattern.ask


object FockActor {
  def mockUserInfo(steamId:String):SteamUserInfo =
    SteamUserInfo(steamId, s"user_${steamId}", s"steam.com/${steamId}", "ava.jpg", "ava.jpg", "ava.jpg")
}

class FockActor(steamId:String, glicko:Double, queueMaster:ActorRef) extends Actor {
  import FockActor._

  private val log = Logging(context.system, this)
  private val userInfo = mockUserInfo(steamId)
  private implicit val timeout = Timeout(120 seconds)
  import context.dispatcher
  ask(queueMaster, ("enqueue", userInfo, glicko)).onComplete({
    case Success(r) => self.forward(r)
    case Failure(ex) => log.error(s"${userInfo.steamId} failed with ${ex}")
  })

  override def receive: Receive = {
    case ("challenge", server:String) =>
      log.warning(s"user ${steamId} got challenge at ${server}, stopping appropriate actor")
      context.stop(self)
    case ("failed", reason:String) =>
      log.error(s"${steamId} failed with ${reason}")
    case _ => ???
  }
}

class FockMasterActor extends Actor {
  private val log = Logging(context.system, this)
  private val remoteQueuePath = context.system.settings.config.getString("q3mm.queueUri")
  private implicit  val timeout = Timeout(5 seconds)
  private val queueProxy = Await.result(context.actorSelection(remoteQueuePath).resolveOne(), timeout.duration)

  // schedule every second
  import context.dispatcher
  context.system.scheduler.schedule(1 seconds, 1 seconds, self, "spawn_focker")

  // spawn a fockers!
  override def receive: Receive = {
    case "spawn_focker" => {
      val glicko = 1000 + Random.nextInt(1000)
      val steamId = Random.alphanumeric.take(16).mkString
      log.info(s"spawning focker with id ${steamId} and glicko ${glicko}")
      context.actorOf(Props(new FockActor(steamId, glicko, queueProxy)))
    }
  }
}

class FockController @Inject() (implicit system: ActorSystem, materializer: Materializer) extends Controller{
  var fockActor = Option.empty[ActorRef]
  def fock = Action { request => {
      if (fockActor.isDefined) {
        Conflict("fock in progress")
      } else {
        fockActor = Some(system.actorOf(Props[FockMasterActor]))
        Ok("focking hard")
      }
    }
  }
}
