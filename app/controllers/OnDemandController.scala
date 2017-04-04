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
import scala.concurrent.duration._
import play.api._
import play.api.libs.ws._
import play.api.mvc._
import play.api.libs.json.Json
import play.api.libs.streams.ActorFlow

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}


class OnDemandController @Inject () (ws:WSClient, configuration: play.api.Configuration)
                                    (implicit system: ActorSystem, materializer: Materializer)
  extends Controller {
  private val remoteInstanceMasterPath = system.settings.config.getString("q3mm.instanceMasterUri")
  private val instanceMasterProxy = system.actorSelection(remoteInstanceMasterPath)

  def getGlicko(steamId:String):Future[Double] = {
    val url = s"http://qlstats.net/player/${steamId}.json"
    val request = ws.url(url)
    Logger.info(s"getting glicko from ${url}")
    request.get().map(response => {
      Try((response.json \ 0 \ "elos" \ "duel" \ "g2_r").as[Double])
        .orElse(Try((response.json \ 0 \ "elos" \ "ffa" \ "g2_r").as[Double]))
        .getOrElse(1500.0)
    })
  }

  def onDemand = Action {
    request => {
      implicit val reads = Json.reads[SteamUserInfo]
      Ok(views.html.onDemand(
        request.session.get("steamUserInfo").flatMap(v =>
          SteamUserInfo.fromJson(v)),
        configuration.underlying.getString("q3mm.wsUrl")))
    }
  }

  def requestServer =
    Action.async({
    implicit request =>
      request.session.get("steamUserInfo").flatMap(SteamUserInfo.fromJson) match {
        case None => {
          Logger.info(s"can not get or deserialize steamUserInfo, forbid access, session is ${request.session.data}")
          Future.successful(Forbidden)
        }
        case Some(userInfo:SteamUserInfo) =>
          getGlicko(userInfo.steamId).map(_.toInt).recover({case _ => Success(1500)}).flatMap(
            glicko => {
              Logger.info(s"on demand server requested by ${userInfo.steamId}")
              val isPrivate = request.getQueryString("isPrivate").contains("true")
              ask(instanceMasterProxy, ("requestServer", List(userInfo), isPrivate, glicko))(60 seconds).map({
                case ("created", server: String) =>
                  Logger.info(s"server created at ${server}")
                  Created(Json.obj("server" -> server).toString)
                case ("failed", reason: String) =>
                  Logger.info(s"server creation failed with reason ${reason}")
                  Conflict(reason)
                case x =>
                  Logger.error(s"unknown message received ${x.toString}")
                  InternalServerError(x.toString)
              }).recover({ case ex => InternalServerError(ex.toString) })
            })
  }})

  def findServer = Action.async({
    implicit request => {
      request.session.get("steamUserInfo").flatMap(SteamUserInfo.fromJson) match {
        case None => Future.successful(Redirect(routes.LoginController.loginPost))
        case Some(userInfo:SteamUserInfo) =>
          implicit val timeout = Timeout(5 seconds)
          ask (instanceMasterProxy, ("findMyServer", userInfo.steamId)).map ( {
            case ("serverAt", uri:String) => Ok(views.html.myserver(Some(uri)))
            case "noServer" => Ok(views.html.myserver(None))
          }).recover({case _ =>Ok(views.html.myserver(None))})
      }
    }
  })

}
