package controllers

import javax.inject._

import play.api._
import play.api.mvc._
import javax.inject.Inject

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api._
import play.api.libs.ws._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.libs.json.Json
import play.api.libs.openid._

import scala.util.matching.Regex


class LoginController @Inject() (ws:WSClient)(openIdClient: OpenIdClient) extends Controller {
  val steamAppKey = "9075032A4866A875DCA3772C0004C665"

  def obtainSteamUserInfo(steamId:String):Future[SteamUserInfo] = {
    val request = ws.url("http://api.steampowered.com/ISteamUser/GetPlayerSummaries/v0002/")
      .withQueryString("key" -> steamAppKey, "steamids" -> steamId)
    request.get().map(response => {
      val player = (response.json \ "response" \ "players")(0)
      SteamUserInfo(
        steamId = steamId,
        personaName = (player \ "personaname").as[String],
        profileUrl = (player \ "profileurl").as[String],
        avatar = (player \ "avatar").as[String],
        avatarMedium = (player \ "avatarmedium").as[String],
        avatarFull = (player \ "avatarfull").as[String]
      )
    })
  }

  def loginPost = Action.async { implicit request =>
    val openIdProvider = "http://steamcommunity.com/openid";
    openIdClient.redirectURL(
      openIdProvider,
      routes.LoginController.openIdCallback.absoluteURL(),
      Seq("email" -> "http://schema.openid.net/contact/email"))
      .map(url => Redirect(url))
  }

  def openIdCallback = Action.async { implicit request =>
    openIdClient.verifiedId(request).flatMap(
      info => {
        val steamIdRegex = new Regex("^http:\\/\\/steamcommunity\\.com\\/openid\\/id\\/(7[0-9]{15,25}+)$")
        val steamId = steamIdRegex.findFirstMatchIn(info.id).get.subgroups(0);
        Logger.info(s"got steam open id callback for steamId ${steamId}")
        obtainSteamUserInfo(steamId).map(
            userInfo => {
              Logger.info(s"got user info ${userInfo}")
              implicit val userInfoWrites = Json.writes[SteamUserInfo]
              Redirect(routes.HomeController.index())
                .withSession("steamUserInfo" -> Json.toJson(userInfo).toString())
            })
      })
  }
}
