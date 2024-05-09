package part5infra

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Dispatcher are in charge of delivering and handling messages within an actor system.
  */
object Dispatchers extends App {
  class Counter extends Actor with ActorLogging {
    var count = 0
    override def receive: Receive = {
      case message =>
        count += 1
        log.info(s"[$count] $message")
    }
  }

  val system = ActorSystem("DispatcherDemo") // ConfigFactory.load().getConfig("dispatcherDemo")

  // method #1 - programming in code
  private val actors = for (i <- 1 to 10) yield system.actorOf(Props[Counter].withDispatcher("my-dispatcher"), s"counter_$i")

//  val r = new Random()
//  for (i <- 1 to 1000) {
//    actors(r.nextInt(10)) ! i
//  }

  // Now in order to test this dispatcher it makes most send to create more than one actor to see how this dispatcher switches the handling in between the actors.
  // 이 디스패처를 테스트하기 위해 여러 actor 를 생성하여 디스패처가 actor 들 사이에서 어떻게 처리를 전환하는지 확인하는 것이 가장 효과적입니다.
  // 여기서 언급된 "디스패처(dispatcher)"는 Akka에서 메시지를 배우들에게 전달하는 역할을 하며, 여러 배우(actors)를 생성함으로써 디스패처가 다양한 배우들 사이에서 메시지를 어떻게 분배하는지를 관찰할 수 있습니다. 이는 디스패처의 효율성과 공정성을 평가하는 데 중요한 방법입니다.

  // 예를 들어, Akka 시스템에서 세 개의 액터(A, B, C)가 있고, 각 액터가 여러 메시지를 받는다고 가정해 보겠습니다. 이 디스패처 설정에 따라, 스레드 풀에는 최대 세 개의 스레드가 활성화되어 각각의 액터가 동시에 실행될 수 있습니다. 액터 A가 먼저 메시지를 받아 시작하면 스레드 풀에서 하나의 스레드가 그 작업을 맡습니다. 만약 액터 A가 30개의 메시지를 처리하기 전에 액터 B와 C도 메시지를 받기 시작하면, 남은 두 스레드가 각각 B와 C에 할당되어 동시 처리가 이루어집니다.

  // method #2 - from config
  val rtjvmActor = system.actorOf(Props[Counter], "rtjvm")

  /**
    * show you as a slightly separate idea is that dispatchers implement the executionContext trait
    */
  class DBActor extends Actor with ActorLogging {
    implicit val executionContext: ExecutionContext = context.dispatcher // context hold bunch of information including the actor system, the actor dispatcher and so on and so forth

    // solution #1
    // implicit val executionContext: ExecutionContext = context.system.dispatchers.lookup("my-dispatcher") // you can search for a dedicated dispatcher to operate your futures.
    // solution #2
    // use Router

    override def receive: Receive = {
      case message =>
        Future { // this future run on the context dispatcher
          Thread.sleep(5000) // the running of futures inside of actor is generally discouraged but in this particular case there's a problem with blocking calls inside futures because if you're running a future with a long or a blocking call you may starve the contextDispatcher of running threads which are otherwise used for handling messages. The dispatcher is limited and with increased load might be occupied serving messages to actors instead of running your future or the other way around you may starve the delivery of messages to actors.
          log.info(s"Success: $message")
        }
    }
  }

  val dbActor = system.actorOf(Props[DBActor])
//  dbActor ! "the meaning of life is 42"

  val nonBlockingActor = system.actorOf(Props[Counter])
  for (i <- 1 to 1000) {
    val message = s"import message $i"
    dbActor ! message
    nonBlockingActor ! message
  }
  // 여기 제시된 코드에서 DBActor 클래스는 Actor를 상속받아 구현된 액터로, 메시지를 받을 때마다 비동기적으로 데이터베이스 작업을 수행하는 것으로 보입니다. 하지만 이 코드에서는 Future 내부에서 Thread.sleep(5000)을 사용하여 의도적으로 5초간 스레드를 블락킹(차단) 상태로 만듭니다. 이러한 사용 방식은 Akka에서 권장하지 않는 패턴입니다. 여기에는 몇 가지 중요한 문제점이 있습니다:
  // 컨텍스트 디스패처의 스레드 고갈: DBActor에서는 context.dispatcher를 사용하여 Future를 실행합니다. 이 디스패처는 액터 시스템 내의 다른 액터들도 사용하는 공유 리소스입니다. Future 내에서 블락킹 호출(Thread.sleep)을 사용하면 해당 스레드가 그 시간 동안 다른 작업을 수행할 수 없게 됩니다. 이는 액터 시스템의 처리량을 저하시키고, 메시지 처리 지연을 초래할 수 있습니다.
  // 메시지 처리 지연: DBActor가 블락킹 작업으로 인해 스레드를 차지하고 있으면, 동시에 수신되는 다른 메시지들이 대기 상태로 머무를 수 있습니다. 특히 고정된 스레드 풀 크기(fixed-pool-size = 3)를 사용하는 경우, 이러한 블락킹 작업이 스레드를 소모하면 다른 액터들도 영향을 받아 메시지 처리가 지연될 수 있습니다.
  //
}
