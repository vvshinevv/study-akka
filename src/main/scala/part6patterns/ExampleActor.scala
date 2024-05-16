package part6patterns

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.pattern.pipe

import scala.concurrent.Future

object ExampleActor extends App {

  class ExampleActor extends Actor with ActorLogging {
    import context.dispatcher

    var state: Int = 0

    def receive: Receive = {
      case "increment" =>
        val originalSender = sender()
        Future {
          val newState = state + 1
          log.info(s"[${Thread.currentThread().getName}] Incrementing state from $state to $newState") // state 변경 로깅
          state = newState
          state
        }.pipeTo(originalSender)
    }
  }

  val system = ActorSystem("ExampleActorSystem")
  val exampleActor = system.actorOf(Props[ExampleActor], "exampleActor")

  exampleActor ! "increment"
  exampleActor ! "increment"
  exampleActor ! "increment"

  Thread.sleep(1000) // 잠시 대기하여 비동기 작업이 완료될 시간을 줍니다.
}
