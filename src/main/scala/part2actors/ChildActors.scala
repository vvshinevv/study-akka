package part2actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import part2actors.ChildActors.CreditCard.{AttachToAccount, CheckStatus}

object ChildActors extends App {

  // Actors can create other actors
  private object Parent {
    case class CreateChild(name: String)
    case class TellChild(message: String)
  }

  private class Parent extends Actor {
    import Parent._

    override def receive: Receive = {
      case CreateChild(name) =>
        println(s"${self.path} creating child")
        // create a new actor right HERE
        val childRef = context.actorOf(Props[Child], name)
        context.become(withChild(childRef))
    }

    def withChild(childRef: ActorRef): Receive = {
      case TellChild(message) => childRef forward message
    }
  }

  class Child extends Actor {
    override def receive: Receive = {
      case message: String => println(s"${self.path} I got: $message")
    }
  }

  import Parent._
  val system = ActorSystem("ParentChildDemo")
  val parent = system.actorOf(Props[Parent], "parent")
  parent ! CreateChild("child")
  parent ! TellChild("hey kid!")

  // actor hierarchies
  // we have a parent actor which supervises or creates or owns a child actor.
  // so we have this parent and then child descendants hierarchy.
  // parent -> child1 -> grand child1
  //        -> child2
  // Now, a natural question that might arise. child is owned by parent. but who owned parent?
  // Is parent some kind of top level actor or something? and the answer is no.
  // We have what we call guardian actor (top level actor)
  // Every akka actor system has three guardian actors.
  // One of them is  `/system` which is the system guardian. So every akka actor system has its own actors for managing various things.
  // for example managing the logging system. So the logging system in akka is also implemented using actors.
  // and the `/system` manages all these system actors.

  // Another guardian actor is  `/user` which is very important for us. This is user level guardian.
  // Every actor what we create using `system.actorOf` are actually owned by this  `/user` which explains this path right over here.
  // So, when we created a parent using `system.actorOf` its path is `/user/parent`
  // `/user` is the top-level guardian actor for every single actor that we as programmers create.

  // And the third guardian actor is `/` which is root guardian.
  // The root guardian manages both the system level guardian and the user level guardian.
  // So system and user sit below the root guardian.
  // The root guardian sits at the level of the actor system itself.
  // So If the root guardian throws an exception or dies in some other way, the whole actor system is brought down.
  // We will talk about how actors can die or about how they're managed or supervised later on in this course.
  // But for now just remember the hierarchy between these actors. The root guardian manages the system and user actor. And the user actor manages every single actor that we create.

  // ---
  // The second thing that I wanted to show you is a feature that Akka offers to find an actor by a path.This is called actorSelection.
  // This actorSelection is a wrapper over a potential actor ref that we can use to send a message.
  // We can send it a message after having located it using its path.
  // So we can do `system.actorSelection` to locate an actor using a path and then we can use that to send a message.
  val childSelection = system.actorSelection("/user/parent/child2")
  childSelection ! "I found you!"

  // If the path happens to be invalid, let's say we have `/user/parent/child2` or something like that, then this.
  // actorSelection object will contain no actor and any message that we send to that will be dropped.
  // If this childSelection doesn't have any actor ref under the hood, then this message will be sent to deadLetters.

  // ---
  // Now the third thing that I wanted to show you is a piece of danger.
  // NEVER PASS MUTABLE ACTOR STATE, OR THE `THIS` REFERENCE, TO CHILD ACTOR.
  // NEVER IN YOUR LIFE.
  // This has the danger of breaking actor encapsulation because the child actor suddenly has access to the internals of the parent actor,
  // so it can mutate the state or directly call methods of the parent actor without sending a messages.
  // And this breaks our very sacred actor principles.

  object NativeBankAccount {
    case class Deposit(amount: Int)
    case class Withdraw(amount: Int)
    case object InitializeAccount
  }

  class NativeBankAccount extends Actor {
    import CreditCard._
    import NativeBankAccount._

    var amount = 0
    override def receive: Receive = {
      case InitializeAccount =>
        val creditCardRef = context.actorOf(Props[CreditCard], "card")
        creditCardRef ! AttachToAccount(this)
      case Deposit(funds)  => deposit(funds)
      case Withdraw(funds) => withdraw(funds)
    }

    def deposit(funds: Int): Unit = {
      println(s"${self.path} depositing $funds on top of $amount")
      amount += funds
    }
    def withdraw(funds: Int): Unit = {
      println(s"${self.path} withdrawing $funds from $amount")
      amount -= funds
    }
  }

  object CreditCard {
    // Let's say our junior programmer in your team defines these actors and is at this very state in the IDE where he hits enter and lets the IDE autocomplete for him.
    // So if he hits enter, then the attachToAccount case class will receive an argument of type NativeBankAccount which is of type actor.


    // Let's analyze how this problem came to be.
    // The problem originated in this message definition because instead of an actor reference this message contains an instance of an actual actor JVM object.
    // So normally we would do bank account reference as an actor ref not as an instance of the actual bank account class.
    case class AttachToAccount(bankAccount: NativeBankAccount) // !!
    case object CheckStatus
  }

  class CreditCard extends Actor {
    override def receive: Receive = {
      case AttachToAccount(account) => context.become(attatchedTo(account))
    }

    def attatchedTo(account: NativeBankAccount): Receive = {
      case CheckStatus =>
        println(s"${self.path} your messages has been processed.")

        // This line is a problem because our bank account suddenly has less money because we have directly call a method on it.
        // This is wrong on so many levels. This behavior in which an actor's state suddenly becomes changed without the use of messages is extremely hard to debug.
        // specially in a critical environment like a banking system.
        // Now event if this line, this call had been benign for example, if you wanted to pay with a credit card and you did `account.withdraw()`
        // the actual amount the real amount that you were requesting. this is still a hug problem because calling a method directly on an actor
        // bypasses all the logic and all the potential security checks that it would normally do in its received message handler.
        // So you're bypassing all the banking processes it causes security issues and it can end up losing your customer's money which can cause your legal problems
        // and it can get you into actual trouble.
        account.withdraw(1)
    }
  }

  import CreditCard._
  import NativeBankAccount._

  Thread.sleep(500)
  println("Thread.sleep(500) =========================")
  val bankAccountRef = system.actorOf(Props[NativeBankAccount], "account")
  bankAccountRef ! InitializeAccount
  bankAccountRef ! Deposit(100)

  Thread.sleep(500)
  val ccSelection = system.actorSelection("/user/account/card")
  ccSelection ! CheckStatus


  // 문제 분석
  // `case class AttachToAccount(bankAccount: NativeBankAccount)`
  // So when you first use this attached to account message

  // `AttachToAccount(this)`
  // It's in here in the receive handler of the NativeBankAccount which compounds the mistake by passing the `this` reference to the child actor.
  // When you use the `this` reference in a message you're basically exposing yourself to method calls from other actors which basically means method calls from other threads.
  // That means that you're exposing yourself to concurrency issues which we wanted to avoid in the first place.
  // This is breaking the actor encapsulation and every single actor principle.
  // because we mentioned at the very beginning that every single interaction with an actor muse happen through messages never through calling methods.

  // Which brings us to step number 3 of the problem.
  // Calling a method directly on an actor instance. We never directly call methods on actors. We only send messages.
  // This problem is a huge one and it extends to both `this` reference and any mutable state of an actor.
  // This is called `Closing Over` mutable state or the `this` reference.
  // Scala doesn't prevent this at compile time so it's our job to make user we keep the actor encapsulation.
  // So the lesson from this exercise is never close over mutable state or the this reference.


  // RECAP
  // Actor can create other actors by using `context.actorOf` and you can use that as you would use `system.actorOf`and this creates child actors under parent actors.
  // And we learned about the actor hierarchies and top lever supervisors(guardian).
  // The system guardian, the user guardian which is the parent of every single actor that we created
  // And the root guardian slash which manages the other two actors.
  // Then having learned about the actor hierarchies we quickly explained actor path which always start with `/user/parent/child` and then all the actors in the hierarchy as sub paths.
  // We then learned how to find actor by giving a path using `system.actorSelection`.
  // I haven't demoed this in the code but this works with `context.actorSelection` just as well.
  // And then we saw our first case of an actor encapsulation break which tends to happen a lot especially in the context of parent-child relationships.

  //
}
