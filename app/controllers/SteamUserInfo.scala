package controllers

object SteamUserInfo {

}


case class SteamUserInfo(steamId:String,
                         personaName:String,
                         profileUrl:String,
                         avatar:String,
                         avatarMedium:String,
                         avatarFull:String)
