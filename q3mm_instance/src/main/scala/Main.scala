import akka.actor._

object Main extends App {
  val system = ActorSystem("q3mm")
  system.actorOf(Props[InstanceActor], name = "instance")
  Console.println("instance actor is running")
}