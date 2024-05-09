package part5infra

import akka.actor.{Actor, ActorLogging, ActorSystem, Cancellable, Props, Timers}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.language.postfixOps

object TimerSchedulers extends App {

  class SimpleActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(message.toString)
    }
  }

  val system = ActorSystem("SchedulerTimerDemo")
  val simpleActor = system.actorOf(Props[SimpleActor])

  system.log.info("Scheduling reminder for simpleActor")

  implicit val ec: ExecutionContextExecutor = system.dispatcher // dispatcher implements the execution context which means they can schedule actions and they can run things on threads

  system.scheduler.scheduleOnce(delay = 1 second) { // 단일 예약 (Single Scheduling): system.scheduler.scheduleOnce을 사용하여 특정 시간 후에 단일 작업(예: 메시지 전송)을 예약합니다.
    simpleActor ! "reminder"
  }(system.dispatcher) // system.dispatcher implements the execution context interface
  // 방법1) implicit val ec: ExecutionContextExecutor = system.dispatcher
  // 방법2) import system.dispatcher

  // this case is very useful for architectures with uptime regularly checked with heartbeat messages.
  val routine: Cancellable = system.scheduler.schedule(1 seconds, 2 seconds) { // 반복 예약 (Repeated Scheduling): system.scheduler.schedule을 사용하여 초기 지연 후 주기적으로 반복되는 작업을 예약합니다.
    simpleActor ! "heartBeat"
  }

  system.scheduler.scheduleOnce(5 seconds) { // 취소 가능 (Cancellable): 스케줄된 작업은 Cancellable 객체를 통해 취소할 수 있습니다.
    routine.cancel() // don't run
  }

  // Things to bear in mind 스케줄된 작업 내에서 불안정한 참조를 사용하지 마세요. 예를 들어, 스케줄이 실행 중인 동안 해당 액터가 종료되고 해제된다면 이는 좋지 않습니다.
  // 1. Don't use unstable reference inside scheduled actions.
  // For example if an actor terminated and is released while a schedule using that actor is running, that's bad.
  //
  // 2. All scheduled tasks execute when the system is terminated regardless of the initial delay. 모든 스케줄된 작업은 시스템이 종료될 때, 초기 지연 시간에 관계없이 실행됩니다.
  // 3. Schedules are not really millisecond precise nor are they usable long term. 스케줄은 정확히 밀리초 단위로 설정되지 않으며, 장기간 사용에 적합하지 않습니다.
  // For example, for messages months in advance. They were simply not conceived for that. 예를 들어, 몇 달 후의 메시지를 위해 사용되는 것은 원래 그 목적이 아닙니다.

  /**
    * Implement a self-closing actor
    * - If the actor receives a message (can be anything), you have 1 second to send it another message 액터가 메시지(어떤 것이든 될 수 있음)를 받으면, 다른 메시지를 보낼 수 있는 시간은 1초입니다.
    * - If the time window expires, the actor will stop itself
    * - If you send another message, the time window is reset. And you have one more second to send it another messages. And so on and so forth.
    */
  class SelfClosingActor extends Actor with ActorLogging {

    var schedule = createTimeoutWindow()

    def createTimeoutWindow(): Cancellable = {
      context.system.scheduler.scheduleOnce(1 seconds) { // This is how you get hold of the actor system within the context.
        self ! "timeout"
      }
    }

    override def receive: Receive = {
      case "timeout" =>
        log.info("Stopping myself")
        context.stop(self)
      case message =>
        log.info(s"Received $message, staying alive")
        schedule.cancel()
        schedule = createTimeoutWindow()
    }
  }

  val selfClosingActor = system.actorOf(Props[SelfClosingActor], "selfClosingActor")
  system.scheduler.scheduleOnce(250 millis) {
    selfClosingActor ! "ping"
  }

  system.scheduler.scheduleOnce(2 seconds) {
    system.log.info("sending pong to self-closing actor")
    selfClosingActor ! "pong"
  }

  /**
    * For actor to send messages to itself and that is called a timer. The rationale for that is that the lifecycle if scheduled messages is very difficult to maintain if the actor is killed or restarted. 액터가 자기 자신에게 메시지를 보내는 것을 '타이머'라고 합니다. 그 이유는 스케줄된 메시지의 생명주기를 유지하기가 매우 어렵기 때문입니다, 특히 액터가 종료되거나 재시작될 때는 더욱 그렇습니다.
    * And the timers are a simpler and safer way to schedule messages to self from within an actor. 타이머는 액터 내부에서 자기 자신에게 메시지를 스케줄하는 더 간단하고 안전한 방법입니다
    */
  case object TimerKey
  case object Start
  case object Reminder
  case object Stop
  class TimerBasedHeartbeatActor extends Actor with ActorLogging with Timers {
    // If I want to schedule a message to myself, I will use a member called timers from the timers trait. 타이머 트레이트의 'timers'라는 멤버를 사용하여 자신에게 메시지를 예약하고 싶을 때 사용합니다.

    timers.startSingleTimer(TimerKey, Start, 500 millis) // 이 코드는 액터가 처음 시작될 때, Start 메시지를 자신에게 보내도록 예약합니다. 이 메시지는 반초(500 밀리초) 후에 전송됩니다.

    override def receive: Receive = {
      case Start =>
        log.info("Bootstrapping.")
        // same TimerKey that was associated to another timer. The previous timer is cancelled which is very cute. Because we don't need to concern ourselves with the lifecycle of a timer. The timer is automatically canceled. 이미 다른 타이머와 연결되어 있던 같은 TimerKey를 사용합니다. 이전 타이머는 자동으로 취소됩니다. 이것은 매우 편리합니다. 왜냐하면 우리는 타이머의 생명주기에 대해 걱정할 필요가 없기 때문입니다. 타이머는 자동으로 취소됩니다.
        timers.startPeriodicTimer(TimerKey, Reminder, 1 seconds) // Reminder 메시지를 1초 간격으로 주기적으로 자신에게 보내도록 설정합니다.
      case Reminder =>
        log.info("I am alive")
      case Stop =>
        log.warning("Stopping!")
        timers.cancel(TimerKey) // This will free up the timer associated to this TimerKey And Just context.stop(self) 이는 TimerKey에 연결된 타이머를 해제하고, context.stop(self)를 통해 단순히 자기 자신의 액터를 중지합니다.
        context.stop(self)
    }
  }

  val timerHeartbeatActor = system.actorOf(Props[TimerBasedHeartbeatActor], "timerActor")
  system.scheduler.scheduleOnce(5 seconds) {
    timerHeartbeatActor ! Stop
  }

  // Recap
  // We learned how to schedule an action at a well defined point in the future
  // by using system.scheduler.scheduleOnce(1 seconds) {} with an initial delay.
  //
  // Repeated action
  // by using system.scheduler.schedule(1 seconds, 2 seconds) with an initial delay and an interval.
  //
  // Schedulers are cancelable which we can invoke by calling schedule.cancel()

  // Then we learned how to use timers which are tool to schedule messages to the actor's own self reference, from within the actor.
  // To keep a better time management of schedulers and we learned how to do that by mixing in the timers trait and by using timers.startSingleTimer or startPeriodicTimer.
  // Both of these take 3 arguments. First is the timer key used for timers management and the message that you want to send.
  // And then the duration which is relevant for the situation. either the initial delay for the single timer or the interval for the periodic timer.
  // And the timers are cancelable by simply calling timer.cancel(MyTimerKey)
}
