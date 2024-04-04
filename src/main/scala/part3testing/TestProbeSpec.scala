package part3testing

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

class TestProbeSpec extends TestKit(ActorSystem("TestProbeSpec")) with ImplicitSender with WordSpecLike with BeforeAndAfterAll {

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  import TestProbeSpec._

  "A master actor" should {
    "register a slave" in {
      val master = system.actorOf(Props[Master])
      val slave = TestProbe("slave") // A TestProbe is a special actor with some assertion capabilities.

      master ! Register(slave.ref) // we can test the interaction between the master and the slave test probe.
      expectMsg(RegistrationAck)
    }

    "send the work to the slave actor" in {
      val master = system.actorOf(Props[Master])
      val slave = TestProbe("slave")

      master ! Register(slave.ref)
      expectMsg(RegistrationAck)

      val workLoadingString = "I love Akka"
      master ! Work(workLoadingString) // sender of this thing is the implicit sender which is if you remember testActor
      // testActor is the member of test kit which is implicitly passed as the sender of every single message that we send to the actor under the test

      // the interaction between the master and the slave actor
      slave.expectMsg(SlaveWork(workLoadingString, testActor))

      // the slave test probe to reply with another message.
      slave.reply(WorkCompleted(3, testActor))

      expectMsg(Report(3))
    }

    "aggregate data correctly" in {
      val master = system.actorOf(Props[Master])
      val slave = TestProbe("slave")

      master ! Register(slave.ref)
      expectMsg(RegistrationAck)

      val workLoadString = "I love Akka"
      master ! Work(workLoadString)
      master ! Work(workLoadString)

      slave.receiveWhile() {
        case SlaveWork(`workLoadString`, `testActor`) => slave.reply(WorkCompleted(3, testActor))
      }
      expectMsg(Report(3))
      expectMsg(Report(6))
    }

  }


}

object TestProbeSpec {
  // testing scenario
  /*
    word counting actor hierarchy master-slave
    send some work to the master
     - master sends the slave the piece of work
     - slave processes the work and replies to master
     - master aggregates the result
    master sends the total count to the original requester
   */

  case class Register(slaveRef: ActorRef)
  case class Work(text: String)
  case class SlaveWork(text: String, originalRequester: ActorRef)
  case class WorkCompleted(count: Int, originalRequester: ActorRef)
  case object RegistrationAck
  case class Report(totalCount: Int)

  class Master extends Actor {
    override def receive: Receive = {
      case Register(slaveRef) =>
        println(sender())
        sender() ! RegistrationAck
        context.become(online(slaveRef, 0))
      case _                  => // ignore
    }

    def online(slaveRef: ActorRef, totalWordCount: Int): Receive = {
      case Work(text) => slaveRef ! SlaveWork(text, sender())
      case WorkCompleted(count, originalRequester) =>
        val newTotalWordCount = totalWordCount + count
        originalRequester ! Report(newTotalWordCount)
        context.become(online(slaveRef, newTotalWordCount))
    }
  }

  // class Slave extends Actor => I don't really care about it
  // I only care about my master actor.
  // This(Master) is the actor that I'm supposed to test.
  // Now the trouble is that this master actor interacts with multiple things in the world.
  // It interacts with the original requester of work and it interacts with a slave and We cannot control those.
  // so our general assertion that we learned in the last video will not be sufficient here.
  // we need some kind of entities that will hold a place for a potential slave and then test the interaction between the master and that entity.
  // That entity that we will inject into our testing infrastructure is called a test probe


  // Recap
  // TestProbes are useful for interactions with multiple actors
  // val probe = TestProbe("TestProbName")
  //
  // TestProbes are fictitious actor with assertion capabilities because test probes can send messages or reply with a message to the probes last sender.
  // probe.send(actorUnderTest, "a message")
  // probe.reply("a message") <- to its last sender
  // testProbes have the same assertion capabilities as the test actor that we saw in the last video.
}
