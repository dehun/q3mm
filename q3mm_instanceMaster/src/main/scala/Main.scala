import akka.actor._

object Main extends App {
  val system = ActorSystem("q3mm")
  system.actorOf(Props[InstanceMasterActor], name = "instanceMaster")
  Console.println("hello world!")
}