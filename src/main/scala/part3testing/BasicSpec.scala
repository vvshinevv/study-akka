package part3testing

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Random

// ImplicitSender is used for send reply scenarios in Actors, which are a lot of course.
// WordSpecLike allow the description of tests in a very natural language style, behavior-driven testing.
// BeforeAndAfterAll supplies some hooks so that when you run your test suit, the set of hooks will be called.
class BasicSpec extends TestKit(ActorSystem("BasicSpec")) with ImplicitSender with WordSpecLike with BeforeAndAfterAll {

  // this is used for destroying and tearing down the test suite. and when the test suite is being instantiated, this new ActorSystem will be created.
  // so naturally, at the end, at the tear down, we need to terminate the ActorSystem.
  // this teardown method is basic setup
  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system) // system is member of TestKit
  }

  import BasicSpec._
  // now the general structure of a test suite follows the pattern of ScalaTest.
  "An simple actor" should {
    "send back the same message" in {
      val echoActor = system.actorOf(Props[SimpleActor])
      val message = "hello, test"
      echoActor ! message

      expectMsg(message) // akka.test.single-expect-default

      // who is at the receiving end of these expectations assertions. because we're sending a bunch of message. but who is the sender of these messages. and who is at the receiving end of the messages that we expect.
      // and the answer is the testActor. testActor is a member of testKit. and is and actor used for communication with the actors that we want to test.
      // testACtor in particular for our tests is also passed implicitly as the sender of every single message that we send.
      // because we mixed in the implicitSender trait. so this is what the implicitSender trait is doing.
      // it's passing testActor as the implicit sender for every single message.
    }
  }

  "A BlackHole actor" should {
    "send back some mesasge" in {
      val blackHoleActor = system.actorOf(Props[BlackHoleActor])
      val message = "hello, test"
      blackHoleActor ! message

      expectNoMessage(1 second)
    }
  }

  // message assertions
  "A lab test actor" should {
    // we don't need to new actors avery single test.
    // but if you have stateful actors that you want to clear after every test.
    // you would do that inside every test.
    val labTestActor = system.actorOf(Props[LabTestActor])
    "turn a string into uppercase" in {
      labTestActor ! "I love Akka"
      expectMsg("I LOVE AKKA")
    }

    "reply to a greeting" in {
      labTestActor ! "greeting"
      expectMsgAnyOf("hello", "hi")
    }

    "reply with favorite tech" in {
      labTestActor ! "favoriteTech"
      expectMsgAllOf("Scala", "Akka")
    }

    "reply with cool tech in a different way" in {
      labTestActor ! "favoriteTech"
      val messages = receiveN(2) // Seq[Any]

      // free to do more complicated assertions
    }

    "reply with cool tech in a fancy way" in {
      labTestActor ! "favoriteTech"
      expectMsgPF() {
        case "Scala" => // only care that the PF is defined
        case "Akka"  =>
      }
    }
  }
}

// I'm going to also recommend that you create a companion object for your specs.
// The reason for that is that in the companion object, I generally recommend you store all the information or all the method or all the values that you are going to use in your tests.
object BasicSpec {

  class SimpleActor extends Actor {
    override def receive: Receive = {
      case message => sender() ! message
    }
  }

  private class BlackHoleActor extends Actor {
    override def receive: Receive = Actor.emptyBehavior
  }

  private class LabTestActor extends Actor {
    private val random = new Random()
    override def receive: Receive = {
      case "greeting" =>
        if (random.nextBoolean()) sender() ! "hi" else sender() ! "hello"
      case "favoriteTech" =>
        sender() ! "Scala"
        sender() ! "Akka"
      case message: String => sender() ! message.toUpperCase()
    }
  }
}

// Recap
//