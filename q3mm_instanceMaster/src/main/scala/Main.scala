import akka.actor._

object Main extends App {
  Console.println("instance master raising!")
  val system = ActorSystem("q3mm")
  system.actorOf(Props[InstanceMasterActor], name = "instanceMaster")
}