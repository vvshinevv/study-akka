package part4faulttolaerance

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, OneForOneStrategy, Props, SupervisorStrategy}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.util.Random

object ActorTest extends App {

  val system = ActorSystem("RetrySystem")
  private val parentActor = system.actorOf(Props[ParentActor], "parentActor")

  // 부모 액터에 메시지 전송
  parentActor ! "Hello, Akka!"

  private case class ProcessMessage(msg: String, attempt: Int = 1)
  private case class ChildFailed(msg: String, attempt: Int, child: ActorRef)

  private class ParentActor extends Actor with ActorLogging {

    override val supervisorStrategy: SupervisorStrategy = OneForOneStrategy(maxNrOfRetries = 2) {

      case ex: RuntimeException =>
        log.info("부모가 자식에게 재시작 명령")
        SupervisorStrategy.Restart
    }

    override def receive: Receive = {
      case msg: String =>
        val childActor = context.actorOf(Props[ChildActor], "childActor")
        log.info("메시지 보낸당!!!")
        childActor ! ProcessMessage(msg)

      case ChildFailed(msg, attempt, child) =>
        val baseDelay = 2.seconds
        val factor = 0.5 + Random.nextDouble() // 0.5 <= factor < 1.5
        val randomDelay = baseDelay + factor
        context.system.scheduler.scheduleOnce(randomDelay, child, ProcessMessage(msg, attempt + 1))
    }
  }

  class ChildActor extends Actor with ActorLogging {

    override def preStart(): Unit = log.info(">>> preStart")

    override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
      log.info(">>> preRestart")
      message.foreach {
        case msg: ProcessMessage => context.parent ! ChildFailed(msg.msg, msg.attempt, self)
        case _                   => // 다른 메시지는 무시
      }
      super.preRestart(reason, message)
    }

    override def receive: Receive = {
      case ProcessMessage(msg, attempt) =>
        log.info("메시지 받았당!")
        throw new RuntimeException("처리 중 에러 발생")
    }

    override def postRestart(reason: Throwable): Unit = log.info(">>> postRestart")

    override def postStop(): Unit = log.info(">>> postStop")
  }
}
