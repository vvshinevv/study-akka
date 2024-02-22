package part2actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object ActorCapabilities extends App {

  private class SimpleActor extends Actor {
    override def receive: Receive = {
      case "Hi!"                              => context.sender() ! "Hello, there!" // replying to message
      case message: String                    => println(s"[${context.self}] I have receive $message")
      case number: Int                        => println(s"[simple actor] I have receive a NUMBER $number")
      case SpecialMessage(contents)           => println(s"[simple actor] I have receive something SPECIAL: $contents")
      case SendMessageToYourself(content)     => self ! content
      case SayHiTo(ref)                       => (ref ! "Hi!")(self)
      case WirelessPhoneMessage(content, ref) => ref forward (content + "s") // I keep the original sender
    }
  }

  private val system = ActorSystem("actorCapabilitiesDemo")
  private val simpleActor = system.actorOf(Props[SimpleActor], "simpleActor")

  simpleActor ! "hello, actor"

  // 1 - message can be of any type
  // a) message must be IMMUTABLE
  //    - so nobody can touch it
  // b) message must be SERIALIZABLE
  //    - serializable means that the JVM can transform it into a byte stream and send it to another JVM where it's on the same machine or over the network
  simpleActor ! 42

  private case class SpecialMessage(contents: String)
  simpleActor ! SpecialMessage("some special content")

  // 2 - actors have information about their context and about themselves
  // each actor has a member called context. This context is a complex data structure that has references to information regarding the environment this actor runs in.
  // for example it has access to the actor system. very importantly this context has access to this actor's own actor reference. This thing is called self.
  // we can use `context.self` but we can also use `self` which is exactly same.

  private case class SendMessageToYourself(content: String)
  simpleActor ! SendMessageToYourself("I am actor and I am proud of it")

  // 3 - actor can REPLY to messages
  val alice = system.actorOf(Props[SimpleActor], "alice")
  val bob = system.actorOf(Props[SimpleActor], "bob")

  // ActorRef : This Actor references are used by Akka to know which actor to send messages to
  // Every actor at any moment in time `context.sender()` contains the actor reference of the actor who last sent a message to me.
  private case class SayHiTo(ref: ActorRef)
  alice ! SayHiTo(bob)

  // 4 - dead letters
  // Whenever an actor sends a message to anther actor, they pass themselves as the sender.
  // The tell method receives the message and an implicit sender parameter which is actor.noSender = null
  // Whenever we send message to an actor from another actor, so if this is in the context of Alice and I'm sending a message to Bob.
  // who is the sender in the big context.
  simpleActor ! 42 // who is sender? - we saw that the sender that's passed in has a default value which is `Actor.noSender` = null

  // alice tries to reply to me which is noSender(null) and we get this very nice info log
  // Message [java.lang.String]("hello there!") from Actor[Alice] to Actor[deadLetters] was not delivered. dead letter encountered.
  // dead letter is a fake actor inside akka which takes case to receive the message that are not sent to anyone. This is the garbage pool of messages.
  // If there is no sender the reply will go to dead letters.
  alice ! "Hi!"

  // 5 - forwarding messages
  // forwarding = sending a message with the ORIGINAL sender.
  // if Alice tell "SayHiTo(bob") then Alice say bob tell hi but alice is being passed as the sender.
  alice ! SayHiTo(bob)

  // if I do forwarding I can keep the original sender
  // Message sends using tell (also known as !):
  //
  // A tells message M to B.
  // B tells that message to C.
  // C thinks the sender() of message M is B.
  //
  //
  // Message sends using forward:
  //
  // A tells message M to B.
  // B forwards that message to C.
  // C thinks the sender() of message M is A.
  private case class WirelessPhoneMessage(content: String, ref: ActorRef)
  alice ! WirelessPhoneMessage("Hi", bob)

  /**
   * Exercise
   * 1. a Counter actor
   *  - Increment
   *  - Decrement
   */

}
