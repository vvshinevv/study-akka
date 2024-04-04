package part3testing

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

class TimedAssertionsSpec
    extends TestKit(ActorSystem("TimedAssertionsSpec", ConfigFactory.load().getConfig("specialTimedAssertionsConfig")))
    with ImplicitSender
    with WordSpecLike
    with BeforeAndAfterAll {
  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  import TimedAssertionsSpec._

  "A worker actor" should {
    val workerActor = system.actorOf(Props[WorkerActor])

    "reply with the meaning of life in a timely manner" in {
      within(500 millis, 1 second) {
        workerActor ! "work"
        expectMsg(WorkResult(42))
      }
    }

    "reply with valid work at a reasonable cadence" in {

      // within one second, all of these things should happen.
      within(1 second) {
        workerActor ! "workSequence"

        // within two seconds, I want 10 messages at most 500 milliseconds time apart.
        val results: Seq[Int] = receiveWhile[Int](max = 2 seconds, idle = 500 millis, messages = 10) {
          case WorkResult(result) => result
        }

        assert(results.sum > 9)
      }
    }

    "reply to a test probe in a timely manner" in {
      within(1 second) {
        val probe = TestProbe()
        probe.send(workerActor, "work")
        probe.expectMsg(WorkResult(42)) // timeout of 0.3 seconds
      }
    }
  }
}

object TimedAssertionsSpec {
  // testing scenario
  // In practice, we have a lot of actors that take a lot of time to respond to a query for a large chunk of work.
  // For example, if they're doing hard computations or waiting for resource.
  // And then there are actors which reply to a work request in a rapid fire succession of smaller chunks of work.

  case class WorkResult(result: Int)

  class WorkerActor extends Actor {

    override def receive: Receive = {
      case "work" =>
        // long computation
        Thread.sleep(500)
        sender ! WorkResult(42)

      case "workSequence" =>
        val r = new Random()
        (1 to 10).foreach { _ =>
          Thread.sleep(r.nextInt(50))
          sender() ! WorkResult(1)
        }
    }
  }
}


// Recap
// put a time cap on the assertions
// within(500 millis, 1 second) { }
//
// receive and process messages during a time window
// val result = receiveWhile[Int](max = 2 seconds, idle = 500 millis, messages = 10) { case WorkResult( ... ) => }

// TestProbes do not listen to the configuration from the within block.
// They listen to their own configuration.