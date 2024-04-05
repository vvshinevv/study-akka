package part3testing

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{CallingThreadDispatcher, TestActorRef, TestProbe}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}
import scala.concurrent.duration._

class SynchronousTestSpec extends WordSpecLike with BeforeAndAfterAll {

  implicit val system: ActorSystem = ActorSystem("SynchronousTestSpec")

  override protected def beforeAll(): Unit = {
    system.terminate()
  }
  import SynchronousTestSpec._
  "A count" should {
    "synchronously increase its counter" in {
      val counter = TestActorRef[Counter](Props[Counter])
      counter ! Inc // counter has already receive the message
      // sending a message to TestActorRef happens in the calling thread.
      // I can make assertions on this counter and naturally the testActorRef is enhanced with some extra capabilities.

      // testActorRef only work in the calling thread
      // when you send a message to it. the thread will only return after the message has been processed.

      assert(counter.underlyingActor.count == 1)

      "synchronously increase its counter at the call of the receive function" in {
        // TestActorRef are so powerful that it can invoke the receive handler on the underlyingActor directly.
        val counter = TestActorRef[Counter](Props[Counter])
        counter.receive(Inc)
        assert(counter.underlyingActor.count == 1)
      }


      "work on the calling dispatcher" in {
        // whatever message I sent to this counter will happen on the calling thread
        val counter = system.actorOf(Props[Counter].withDispatcher(CallingThreadDispatcher.Id))
        val probe = TestProbe()
        probe.send(counter, Read)

        // a magical thing happens because due to the fact that the counter operates on the calling thread dispatcher,
        // after this line the probe has already received the count reply because every interaction with the counter happens on the probe's calling thread.
        // the probe will have already received the message zero because this interaction has already happen.
        probe.expectMsg(Duration.Zero, 0)

        // That's because the counter operates on the calling thread.
        // If I remove this line, the test will be fail because if I remove the callingTheadDispatcher then the counter suddenly act in an asynchronous manner
        // which means that will take time for the probe to receive the message zero.
        // the probe has to wait at least a couple of milliseconds to receive the reply.
      }
    }
  }
}

object SynchronousTestSpec {
  case object Inc
  case object Read

  class Counter extends Actor {
    var count = 0

    override def receive: Receive = {
      case Inc  => count += 1
      case Read => sender() ! count
    }
  }
}

// we've learned how to do deterministic synchronous test by making all messages being handled in the calling thread
// option 1 TestActorRef -> once you've sent a message to a test actorRef you are completely sure that the message has already been handled because everything happens on the calling thread.
// option 2 callingThreadDispatcher
