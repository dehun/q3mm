import akka.actor._

object Main extends App {
  val system = ActorSystem("q3mm")
  system.actorOf(Props[QueueActor], name = "queue")
  Console.println("hello world!")
}
