package part2actors

import akka.actor.{Actor, ActorSystem, Props}

object ActorExercises extends App {

  /**
    * 1. Create counterActor
    *  - hold an internal variable and will respond to Increment and Decrement message.
    *  - also print its internal int counter variable
    *  - when receive an Increment or Decrement message It will either increment or decrement its internal variable
    *  - and when it receive the print message It will print its internal variable counter to the console at its current value
    */
  private class CounterActor extends Actor {
    private val count = 0

    override def receive: Receive = onMessage(count)

    private def onMessage(count: Int): Receive = {
      case Increment => context.become(onMessage(count + 1))
      case Decrement => context.become(onMessage(count - 1))
      case Print => println(s"[${context.self}] count: $count")
    }
  }

  private object Increment

  private object Decrement

  private object Print

  private val system = ActorSystem("exercise")
  private val counter = system.actorOf(Props[CounterActor], "counterActor")
  counter ! Decrement
  counter ! Print

  /**
  * 2. Create BackAccount as an actor
  *  - this bank account will be able to receive some messages to deposit amount and withdraw an amount
  *  - and extra message of a statement now rather than print things to the console
  *  - a bank account will react to deposit and withdraw by sending back or replying with a Success or failure of each of these messages of operations
  *
  *  - I want you to design both the receiving and the replies and the logic for the back actor itself.
  *  - So it will probably hold some kind of internal variable to hold the funds and it will change its internal fund depending on the amount in the deposit and withdraw
  *  - but take care of special edge case.
  *
  *  - interact with some other kind of actor which will send a deposit withdraw and statement to the bank account and this other actor will print or interpret the success and failure from it
  */
}
