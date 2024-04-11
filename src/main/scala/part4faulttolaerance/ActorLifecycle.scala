package part4faulttolaerance

import akka.actor.{Actor, ActorLogging, ActorSystem, PoisonPill, Props}

// Actor instance
// - has methods such as the ReceiveMessage Handler and may have internal state and if you remember I've denoted it by diamond shape.
//
// Actor Reference aka incarnation
// - created with system.actorOf or context.actorOf and I've denoted it by this big blue dot.
//   Of course you know by now that Actor are encapsulated and the only way that you can communicate with an actor is via this Actor Reference or Incarnation
// - which means that the Actor Reference is responsible for communication which means this Actor Reference is the one who has the mailbox and can actually receive and process messages.
// - The actor reference contains on Actor instance at any one time and
// - besides that it also contains and very importantly contains a unique identifier given by the ActorSystem. (UUID)
//
// Actor Path
// this basically is a space in the Actor System which may or may not be occupied with an Actor Reference and I'm going to denote the Actor Path by this yellow rounded rectangle.

// Actor can be subject to a number of actions.
// - started
//  ㄴ create a new ActorRef with a UUID at a given path and constructs a new Actor Reference.
// - suspended
//  ㄴ the actor reference may enqueue but will not precess anymore messages.
// - resumed
//  ㄴ opposite of suspended. the actor reference will continue processing more messages.
//  ㄴ it just unblocking the actor reference for processing more messages.
// - restarted
//  ㄴ restarting is trickier
//  ㄴ Let's assume that we have an actor which has an actor instance denoted by that green diamond shape which has an internal state.
//  ㄴ First the Actor Reference is suspended which means it may enqueue but will not process any more messages.
//  ㄴ Then the Actor Instance is swapped in a number of steps.
//  ㄴ - first the old instance calls a lifecycle method called preRestart
//  ㄴ - then the actor instance is released and a new Actor Instance comes back to take its place.
//  ㄴ - then the new Actor instance calls a lifecycle method called postRestart. then the Actor Reference is resumed.
//  ㄴ Now as an effect of restarting any internal state inside of the Actor instance is destroyed because the Actor instance is completely swapped. so that's restarting.
// - stop
//  ㄴ Stopping frees the actor ref within a path
//  ㄴ Stopping also has a small process attached to it and stopping basically releases the Actor reference which occupies a given Actor Path inside of the actor system.
//  ㄴ So let this rounded rectangle be the Actor path
//  ㄴ - stopping means that the actor instance will call lifecycle method called postStop.
//  ㄴ - also all watching actors which have registered for myDeathWatch will receive the Terminated(ref) message which you saw in previous video.
// after both of these step are completed the actorRef maybe released which means the Actor Path can then be occupied by another Actor Reference.
// this has a different unique identifier given by the Actor System. So this is a different Actor Reference which means that as a result of stopping all the messages currently enqueued on that Actor Reference were lost.

object ActorLifecycle extends App {

//  object StartChild
//  class LifecycleActor extends Actor with ActorLogging {
//
//    // this method is called before the Actor Instance has a chance to process any messages.
//    override def preStart(): Unit = log.info("I am starting")
//
//    override def postStop(): Unit = log.info("I have stopped")
//
//    override def receive: Receive = {
//      case StartChild => context.actorOf(Props[LifecycleActor], "child")
//    }
//  }
//
  val system = ActorSystem("ActorLifecycleDemo")
//  val parent = system.actorOf(Props[LifecycleActor], "parent")
//  parent ! StartChild
//  parent ! PoisonPill // the child stop first and then the parent stops.

  println("========================")
  /**
    * restart hook especially in the context of an actor throwing an exception or failing in any way.
    * so for that I am going to create a simple parent child relationship.
    */
  object Fail
  object FailChild
  object CheckChild
  object Check

  class Parent extends Actor {
    private val child = context.actorOf(Props[Child], "supervisedChild")
    override def receive: Receive = {
      case CheckChild => child ! Check
      case FailChild => child ! Fail
    }
  }

  class Child extends Actor with ActorLogging {

    override def preStart(): Unit = log.info("supervised child started")

    override def postStop(): Unit = log.info("supervised child stopped")

    override def preRestart(reason: Throwable, message: Option[Any]): Unit =
      log.info(s"supervised actor restarting because of ${reason.getMessage}")

    override def postRestart(reason: Throwable): Unit = log.info("supervised actor restarted")

    override def receive: Receive = {
      case Fail =>
        log.warning("child will fail now")
        throw new RuntimeException("I failed")

      case Check =>
        log.info("alive and kicking")
    }
  }

  val supervisor = system.actorOf(Props[Parent], "supervisor")
  supervisor ! FailChild
  supervisor ! CheckChild


}

/**
 * 중요!!!
 *
 * even if the child actor threw an exception previously which is a very serious thing the child was still restarted and it was able to process more messages
 * and this is a part of the default what it's called supervision strategy.
 * and the default strategy and the default supervision strategy says the following if an actor threw an exception while processing a message
 * this message which caused the exception to be thrown is removed from the queue and not put back in the mailbox again and the actor is restarted which means the mailbox is untouched.
 *
 * that's the default supervision strategy but supervision strategies are a subject for the next lecture.
 */
