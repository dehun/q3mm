package controllers

import play.api.libs.json._

object SteamUserInfo {
  def fromJson(js:String):Option[SteamUserInfo] = {
    implicit val reader = Json.reads[SteamUserInfo]
    Json.fromJson[SteamUserInfo](Json.parse(js)).asOpt
  }

  def asJson(ui:SteamUserInfo):JsValue = {
    implicit val writer = Json.writes[SteamUserInfo]
    Json.toJson(ui)
  }
}

case class SteamUserInfo(steamId:String,
                         personaName:String,
                         profileUrl:String,
                         avatar:String,
                         avatarMedium:String,
                         avatarFull:String)
