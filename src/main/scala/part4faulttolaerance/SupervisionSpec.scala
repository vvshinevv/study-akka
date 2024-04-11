package part4faulttolaerance

import akka.actor.SupervisorStrategy.{Escalate, Restart, Resume, Stop}
import akka.actor.{Actor, ActorRef, ActorSystem, AllForOneStrategy, OneForOneStrategy, Props, SupervisorStrategy, Terminated}
import akka.testkit.{EventFilter, ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

/**
  * A Parent's Duty
  *
  * The core philosophy of akka is that actor failures are fine, but it's up to us to decide at the parent level.
  * How to handle our children actor's failure.
  *
  * If an actor fails, it first suspends all of its children and send a special message to its parent via a dedicated message queue.
  * Now it's up to the parent to decide what to do with this failure.
  * So the parent can decide between a number of actions
  *  - It can decide to resume the actor as if nothing happened.
  *  - It can decide to restart the actor and clear its internal state, which by default stops all of its children.
  *  - It can decide to stop the actor altogether which also means stopping everything beneath it.
  *  - It can decide to fail itself and escalate the failure to its parent.
  */
class SupervisionSpec extends TestKit(ActorSystem("SupervisionSpec")) with ImplicitSender with WordSpecLike with BeforeAndAfterAll {
  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  import SupervisionSpec._
  "A supervisor" should {
    "resume its child in case of a minor fault" in {
      val supervisor = system.actorOf(Props[Supervisor])
      supervisor ! Props[FussyWordCounter]
      val child = expectMsgType[ActorRef]

      child ! "I love Akka"
      child ! Report
      expectMsg(3)

      child ! "Akka is awesome because I am learning to think in a whole new way"
      child ! Report
      expectMsg(3) // because state of the child which is the fussy word counter has not changed.
    }

    "restart its child in case of an empty sentence" in {
      val supervisor = system.actorOf(Props[Supervisor])
      supervisor ! Props[FussyWordCounter]
      val child = expectMsgType[ActorRef]

      child ! "I love Akka"
      child ! Report
      expectMsg(3)

      child ! ""
      child ! Report
      expectMsg(0)
    }

    "terminate its child in case of a major error" in {
      val supervisor = system.actorOf(Props[Supervisor])
      supervisor ! Props[FussyWordCounter]
      val child = expectMsgType[ActorRef]

      watch(child)
      child ! "akka is nice"

      val terminatedMessage = expectMsgType[Terminated]
      assert(terminatedMessage.actor == child)
    }

    "escalate an error when it doesn't know what to do" in {
      val supervisor = system.actorOf(Props[Supervisor], "supervisor")
      supervisor ! Props[FussyWordCounter]
      val child = expectMsgType[ActorRef]

      watch(child)

      child ! 43
      val terminatedMessage = expectMsgType[Terminated]
      assert(terminatedMessage.actor == child)
    }
  }

  "A kind supervisor" should {
    "not kill children in case it's restared or escalates failures" in {
      val supervisor = system.actorOf(Props[NoDeathOnRestartSupervisor], "supervisor")
      supervisor ! Props[FussyWordCounter]
      val child = expectMsgType[ActorRef]

      child ! "Akka is cool"
      child ! Report
      expectMsg(3)

      child ! 35
      child ! Report
      expectMsg(0)
    }
  }

  "An all-for-one supervisor" should {
    "apply the all-for-on strategy" in {
      val supervisor = system.actorOf(Props[AllForOneSupervisor], "allForOneSupervisor")
      supervisor ! Props[FussyWordCounter]
      val firstChild = expectMsgType[ActorRef]

      supervisor ! Props[FussyWordCounter]
      val secondChild = expectMsgType[ActorRef]

      secondChild ! "Testing supervision"
      secondChild ! Report
      expectMsg(2)

      EventFilter[NullPointerException]() intercept {
        firstChild ! ""
      }

      Thread.sleep(500)

      secondChild ! Report
      expectMsg(0)

      firstChild ! Report
      expectMsg(0)
    }
  }
}

object SupervisionSpec {

  class Supervisor extends Actor {

    override val supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
      case _: NullPointerException     => Restart
      case _: IllegalArgumentException => Stop
      case _: RuntimeException         => Resume
      // When an actor escalates, it stops all its children and escalates the failure to the parent.
      // So I should expect the fussyWordCounter to be dead by the time the supervisor triggers escalates this exception to its parent.
      // Because the escalate strategy escalates the exception to the user guardian and the user guardians default strategies to restart everything,
      // The supervisor's restart method will kill the fussyWordCounter, as I mentioned.
      case _: Exception => Escalate
    }

    override def receive: Receive = {
      case props: Props =>
        val childRef = context.actorOf(props)
        sender() ! childRef
    }
  }

  class NoDeathOnRestartSupervisor extends Supervisor {
    override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
      // empty
    }
  }

  // The difference between a OneForOneStrategy and AllForOneStrategy is that the OneForOneStrategy applies these decision on the exact actor that caused the failure.
  // Whereas AllForOneSupervisor applies theses supervisor strategy for all the actors. Regardless of the one that actually caused the failure.
  // So in case one of the children fails with an exception. All of the children are subject to the same supervision directive.
  class AllForOneSupervisor extends Supervisor {
    override val supervisorStrategy: SupervisorStrategy = AllForOneStrategy() {
      case _: NullPointerException     => Restart
      case _: IllegalArgumentException => Stop
      case _: RuntimeException         => Resume
      case _: Exception                => Escalate
    }
  }

  case object Report
  class FussyWordCounter extends Actor {
    private var words = 0

    override def receive: Receive = {
      case Report => sender() ! words
      case ""     => throw new NullPointerException("sentence is empty")
      case sentence: String =>
        if (sentence.length > 20) throw new RuntimeException("sentence is too big")
        else if (!Character.isUpperCase(sentence(0))) throw new IllegalArgumentException("sentence must start with uppercase")
        else words += sentence.split(" ").length

      case _ => throw new Exception("can only receive strings")
    }
  }
}
