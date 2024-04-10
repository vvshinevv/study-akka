package part4faulttolaerance

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}

object StartingStoppingActors extends App {

  val system = ActorSystem("StartingStoppingActors")


  object Parent {
    case class StartChild(name: String)
    case class StopChild(name: String)
    case object Stop
  }

  class Parent extends Actor with ActorLogging {
    import Parent._


    override def receive: Receive = withChildren(Map())

    def withChildren(children: Map[String, ActorRef]): Receive = {
      case StartChild(name) =>
        log.info(s"Starting child $name")
        context.become(withChildren(children + (name -> context.actorOf(Props[Child], name))))
      case StopChild(name) =>
        log.info(s"Stopping child with the name $name")
        val childOption = children.get(name)
        childOption.foreach(childRef => context.stop(childRef))

        // context.stop si non-blocking method. everything happens asynchronously.
        // when you say context.stop you basically send that signal to the child actor to stop.
        // That doesn't mean that it stopped immediately.

      case Stop =>
        log.info("Stopping myself!")
        context.stop(self)

        // Now the point here with context.stop is again this is async
    }
  }

  class Child extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(message.toString)
    }
  }

  import Parent._
  val parent = system.actorOf(Props[Parent], "parent")
  parent ! StartChild("child1")
  val child = system.actorSelection("/user/parent/child1")
  child ! "hi kid!"

  parent ! StopChild("child1")
  for (_ <- 1 to 50) child ! "are you still there?"
}
