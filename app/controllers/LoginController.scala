package controllers

import javax.inject._

import play.api._
import play.api.mvc._
import javax.inject.Inject

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.libs.openid._

import scala.util.matching.Regex

class LoginController @Inject() (openIdClient: OpenIdClient) extends Controller {

  def loginPost = Action.async { implicit request =>
    val openIdProvider = "http://steamcommunity.com/openid";
    openIdClient.redirectURL(
      openIdProvider,
      routes.LoginController.openIdCallback.absoluteURL(),
      Seq("email" -> "http://schema.openid.net/contact/email"))
      .map(url => Redirect(url))
      //.recover { case t: Throwable => ServiceUnavailable()}

  }

  def openIdCallback = Action.async { implicit request =>
    openIdClient.verifiedId(request).map(
      info => {
        val steamIdRegex = new Regex("^http:\\/\\/steamcommunity\\.com\\/openid\\/id\\/(7[0-9]{15,25}+)$")
        val steamId = steamIdRegex.findFirstMatchIn(info.id).get.subgroups(0);
        Ok(info.id + "\n" + info.attributes)
          .withSession("steamId" -> steamId)
      })
  }
}
