package controllers

import java.time.Duration
import java.util.concurrent
import javax.inject.Inject

import akka.actor.Actor.Receive
import akka.actor._
import akka.pattern.ask
import akka.stream._
import akka.util.Timeout

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import play.api._
import play.api.libs.ws._
import play.api.mvc._
import play.api.libs.json.Json
import play.api.libs.streams.ActorFlow

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}


class OnDemandController @Inject () (configuration: play.api.Configuration)
                                    (implicit system: ActorSystem, materializer: Materializer)
  extends Controller {
  private val remoteInstanceMasterPath = system.settings.config.getString("q3mm.instanceMasterUri")
  private val instanceMasterProxy = system.actorSelection(remoteInstanceMasterPath)

  def onDemand = Action {
    request => {
      implicit val reads = Json.reads[SteamUserInfo]
      Ok(views.html.onDemand(
        request.session.get("steamUserInfo").flatMap(v =>
          SteamUserInfo.fromJson(v)),
        configuration.underlying.getString("q3mm.wsUrl")))
    }
  }

  def requestServer = Action.async({
    implicit request =>
      request.session.get("steamUserInfo") match {
        case None => {
          Logger.info(s"can not get steamUserInfo, forbid access, session is ${request.session.data}")
          Future.successful(Forbidden)
        }
        case Some(userInfoJs) =>
          SteamUserInfo.fromJson(userInfoJs) match {
            case Some(userInfo) =>
              Logger.info(s"on demand server requested by ${userInfo.steamId}")
              ask(instanceMasterProxy, ("requestServer", List(userInfo)))(60 seconds).map({
                  case ("created", server:String) =>
                    Logger.info(s"server created at ${server}")
                    Created(Json.obj("server" -> server).toString)
                  case ("failed", reason:String) =>
                    Logger.info(s"server creation failed with reason ${reason}")
                    InternalServerError(reason)
                  case x =>
                    Logger.error(s"unknown message received ${x.toString}")
                    InternalServerError(x.toString)
                }).recover({case ex => InternalServerError(ex.toString)})
            case None =>
              Future.successful(Forbidden)
          }
  }})

}
