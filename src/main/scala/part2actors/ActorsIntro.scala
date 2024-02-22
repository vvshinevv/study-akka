package part2actors

import akka.actor.{Actor, ActorSystem, Props}

object ActorsIntro extends App {

  // part1 - actor systems
  // Now, every akka application have to start with what is called an actor system
  // The way that we create an actor system is the thing that we did in the playground when we first set up our project, so would create something like an actor-system, and we would say actor-system
  // The actor system is a heavyweight data structure that controls a number of threads under the hood, which then allocate to running actors, we will see how actors work very shortly.
  // Now it's recommended to have one of these actor systems per application instance unless we have good reasons to create more, and also the name of this actor system has restrictions, so it must contain only alphanumeric characters
  // So normal characters and non-leading hyphens or underscores, that's because actors can be located by their actor system, and we cannot have for example space inside, if we try to do this with a space, this program will crash for illegal name
  private val actorSystem = ActorSystem("firstActorSystem")
  println(actorSystem.name)

  // part2 - create actors
  // We are ready to move to the more interesting part two where we create actors within our actor system
  // So as we mentioned in the previous video, actors are kind of like humans talking to each others, so when you want to interact with another human, you send a message
  // for example something like, where can I find the supermarket, and the other actor receives the message when they can hear it, or when they aren't busy doing something else, they think about it or they process your message,
  // and they may or may not reply back, something like go there and take the second right and so on and so forth.
  // So we now remember the main properties of this interaction.

  // First of all, an actor is uniquely identified, much like humans are uniquely identified by their face or by their social security number and things like that.
  // So an actor is uniquely identified within an actor system.
  // Secondly, messages are passed and processed asynchronously, so you send the message when you need, and the actors reply when they can.
  // Third, each actor has a unique behavior or a unique way of processing the message, thinking back to our human interaction, if that human who you're asking where the supermarket is doesn't like your face, they might not reply back, or they might with an angry remark or something like that.
  // And finally, you cannot ever invade the other actor or read their mind or otherwise force them to give you the information that you want.
  private class WordCountActor extends Actor {
    // internal data
    private var totalWords = 0

    // behavior
    override def receive: PartialFunction[Any, Unit] = {
      case message: String =>
        println(s"[word counter] I have received: $message")
        totalWords += message.split(" ").length
      case message => println(s"[word counter] I cannot understand ${message.toString}")
    }
  }

  // part3 - instantiate our actor
  // Now, the difference between actors and normal objects in Akka is that you cannot instantiate an actor by calling new but by using invoking the actor system.
  // `val wordCounter = new WordCounterActor` => doesn't work
  // In order to have a proper actor managed by Akka, you need to have a akka-system and then call its actorOf method and then pass in a props object typed with the actor type that you want to instantiate.
  // Then the other parameter here on the actorOf method is the name of your actor. It's often a good idea to name your actors in practice.
  // And the name restrictions for the actors are the same as for the actor system. So you only can use alphanumeric characters and non-leading hyphens and underscores.
  // Ok, and having done that, the result of this expression is an actor reference. So an actor reference is the data structure that Akka exposes to you as a programmer
  // so that you cannot call or otherwise poke into the actual workCountActor instance that Akka creates.
  // You can only communicate to this actor via this actor reference.
  private val wordCounter = actorSystem.actorOf(Props[WordCountActor], "wordCounter")
  private val anotherWordCounter = actorSystem.actorOf(Props[WordCountActor], "anotherWordCounter")

  // part4 - communicate
  // The way that we communicate with an actor through its actor reference is by saying wordCounter exclamation mark.
  // So this is the method that we use. And then the message that we want to send.
  // Now, it may not be apparent from the simple example, but sending the message here is completely asynchronous.
  wordCounter ! "I am learning Akka and it's pretty damn cool!"
  anotherWordCounter ! "A different message"

  // 정리
  // 1. actors are uniquely identified. There are no two actors with the same name under the same actor system.
  // 2. actors are fully encapsulated. You cannot poke at their data, you cannot call their method, and you cannot even instantiate the actor classes by hand.
  //    Only way that you can communicate with an actor is by sending message via the exclamation method.
  //    The exclamation method is also known as tell, and every actor has internal data and behavior, and the behavior is this method receive, which return an instance of partial function from any to unit.
  //    This is the type that akka will use. This is also aliased by the type receive, which is exactly the same thing.

  // 질문
  // The question is how do we instantiate a actor of type person with constructor arguments?
  // Because if we look at the way that we instantiate actors before, that was with `actorSystem.actorOf(Props[WordCountActor], "wordCount")`
  // The way that we instantiate such an actor is by using props with an argument.

  private class Person(name: String) extends Actor {
    override def receive: Receive = {
      case "hi" => println(s"Hi, my name is $name")
      case _    =>
    }
  }

  // We created a person actor by passing it a props with an actor instance inside.
  // This is legal but this is also discourage.
  private val person = actorSystem.actorOf(Props(new Person("Bob")))
  person ! "hi"

  // The general best practice for creating props with actor instances, especially for these cases where you want to pass in constructor argument is to declare a companion object.
  // This has the advantage that we do not create actor instance ourselves, but the factory method created props with actor instance for us.
  // So instead of props new person Bob, I'm just going to say below.
  // Define a companion object and define a method that based on some arguments, it creates a props object with an actor instance with your constructor arguments.
  private object Person {
    def props(name: String): Props = Props(new Person(name))
  }

  private val _person = actorSystem.actorOf(Person.props("Bob"))
}
