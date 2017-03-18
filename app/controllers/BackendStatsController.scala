package controllers

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.google.inject.Inject
import play.api.mvc.{Action, Controller}

import scala.concurrent.Future
import scala.concurrent.duration._
import akka.pattern.ask
import akka.util.Timeout


class BackendStatsController @Inject() (implicit system: ActorSystem, materializer: Materializer) extends Controller {
  private val remoteInstanceMasterPath = system.settings.config.getString("q3mm.instanceMasterUri")
  private val remoteQueuePath = system.settings.config.getString("q3mm.queueUri")
  private implicit val timeout:Timeout = 30 seconds
  import system.dispatcher

  def stats = Action.async {
    request => {
        (for {
          queueProxy <- system.actorSelection(remoteQueuePath).resolveOne()
          queueStats <- ask(queueProxy, "stats")(timeout).mapTo[QueueStats]
          instanceMasterProxy <- system.actorSelection(remoteInstanceMasterPath).resolveOne()
          instanceMasterStats <- ask(instanceMasterProxy, "stats")(timeout).mapTo[InstanceMasterStats]
        } yield Ok(views.html.backendStats(queueStats, instanceMasterStats)))
          .recover({ case ex => InternalServerError(ex.toString) })
    }
  }
}
