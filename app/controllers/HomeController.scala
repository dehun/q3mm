package controllers

import javax.inject._

import play.api._
import play.api.libs.json.Json
import play.api.mvc._

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject() (configuration: play.api.Configuration) extends Controller {
  def index = about

  //Action {
  //    request => {
  //      implicit val reads = Json.reads[SteamUserInfo]
  //      Ok(views.html.index(
  //        request.session.get("steamUserInfo").flatMap(v =>
  //          SteamUserInfo.fromJson(v)),
  //        configuration.underlying.getString("q3mm.wsUrl")))
  //    }
  //  }

  def about = Action {
    Ok(views.html.about())
  }

  def myStats = Action {
    request => {
      request.session.get("steamUserInfo").flatMap(SteamUserInfo.fromJson) match {
        case None => Redirect(routes.LoginController.loginPost)
        case Some(userInfo:SteamUserInfo) => Redirect(s"http://qlstats.net/player/${userInfo.steamId}#duel")
      }
    }
  }
}
