package part4faulttolaerance

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Kill, PoisonPill, Props, Terminated}
import part4faulttolaerance.StartingStoppingActors.Parent.StartChild

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
      case message => log.info(message.toString)

      // Now the point here with context.stop is again this is async
      // this also stops all of its child actors.
    }
  }

  class Child extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(message.toString)
    }
  }

  /**
    * method #1 - using context.stop
    */
//  import Parent._
//  val parent = system.actorOf(Props[Parent], "parent")
//  parent ! StartChild("child1")
//  val child = system.actorSelection("/user/parent/child1")
//  child ! "hi kid!"
//
//  parent ! StopChild("child1")
////  for (_ <- 1 to 50) child ! "are you still there?"
//
//  parent ! StartChild("child2")
//  val child2 = system.actorSelection("/user/parent/child2")
//  child2 ! "hi second child"
//
//  parent ! Stop
//
//  // should not be received. because these messages will certainly be received after the Stop signal
//  for (_ <- 1 to 10) parent ! "parent are you still there?"
//  for (i <- 1 to 100) child2 ! s"[$i] second kid are you still alive?"

  // the point is that the child stops before the parent actually stops.
  // the context.stop(self) actually stops all the children first and then it stops the parent actors.
  // We will get to a more definitive conclusion on this fact when we study the actual life cycle in the next video.
  // but just know that when you call context.stop(self) this actually wait until all children are stopped first and then it will stop you.

  /**
    * method #2 - using special messages
    */
//  val looseActor = system.actorOf(Props[Child], "looseActor")
//  looseActor ! "Hello, loose actor"
//  looseActor ! PoisonPill // one of the special messages that are handled by actors, and in this case for PoisonPill it will invoke the stopping procedure
//  looseActor ! "loose actor are you still there?"
//
//  val abruptlyTerminatedActor = system.actorOf(Props[Child], "abruptlyTerminatedActor")
//  abruptlyTerminatedActor ! "you are about to be terminated"
//
//  // kill message is different from PoisonPill in that it's a little more brutal and you're going to see in what way.
//  // kill is more brutal than a PoisonPill in that it makes the actor throw this actorKilledException.
//  // and then of course that the next message you have been terminated has never been received and it's logged to deadLetters
//  abruptlyTerminatedActor ! Kill
//  abruptlyTerminatedActor ! "you have been terminated"

  // PoisonPill and kill messages are special, and they're handled separately by actors.
  // So you cannot handle them in your normal receiveMessageHandler.
  // behind the scenes, there's actually a separate receiveMessageHandler that Akka control.
  // So you cannot catch PoisonPill and ignore it

  /**
    * methoid #3 - Dead watch that is a mechanism for being notified when an actor dies.
    */
  class Watcher extends Actor with ActorLogging {
    import Parent._
    override def receive: Receive = {
      case StartChild(name) =>
        val child = context.actorOf(Props[Child], name)
        log.info(s"Started and watching child $name")
        context.watch(child) // register this actor for the death of the child. when the child dies, this actor will receive a special terminated message.

      // Now context.watch is often used in conjunction with its opposite context.unwatch(child)
      // This is very useful when you expect a reply from an actor and until you get a response, you register for its death watch
      // because it might die in the meantime. And when you get the response that you want, you naturally unsubscribe from the actor's death watch because you don't care if it's alive anymore.

      case Terminated(ref) =>
        log.info(s"the reference that I'm watching $ref has been stopped")
    }
  }

  val watcher = system.actorOf(Props[Watcher], "watcher")
  watcher ! StartChild("watchedChild")
  val watchedChild = system.actorSelection("/user/watcher/watchedChild")

  Thread.sleep(500)

  watchedChild ! PoisonPill
}





