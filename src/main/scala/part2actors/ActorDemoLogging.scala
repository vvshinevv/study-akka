package part2actors

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.event.Logging

object ActorDemoLogging extends App {

  // #1 - explicit logging
  private class SimpleActorWithExplicitLogger extends Actor {
    private val logger = Logging(context.system, this)

    override def receive: Receive = {
      /*
        1 - debug
        2 - info
        3 - warn
        4 - error
       */
      case message => logger.info(message.toString) // LOG it
    }
  }

  val system = ActorSystem("LoggingDemo")
  val actor = system.actorOf(Props[SimpleActorWithExplicitLogger])

  actor ! "Logging a simple message"

  // #2 - ActorLogging
  private class ActorWithLogging extends Actor with ActorLogging {
    override def receive: Receive = {
      case (a, b)  => log.info("Two things: {} and {}", a, b)
      case message => log.info(message.toString)
    }
  }

  private val simplerActor = system.actorOf(Props[ActorWithLogging])
  simplerActor ! "Logging a simple message by extending a trait"
  simplerActor ! ("a", "b")


  // ## 요약
  // 1. Logging is done asynchronously to minimize performance impact.
  // In particular, the logging system that we used in this video is implemented using Actor itself -> Akka logging is done with actors!
  // 2. Logging doesn't depend on a particular logger implementation. You can change the logger, e.g. SLF4J
  // The default logger just dumps things to standard output as we saw in this video but we can insert come other logger very easily.
  // We can change that through a very simple configuration.
}
