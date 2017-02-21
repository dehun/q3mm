package controllers

import play.api.libs.json._

case class SteamUserInfo(steamId:String,
                         personaName:String,
                         profileUrl:String,
                         avatar:String,
                         avatarMedium:String,
                         avatarFull:String)
