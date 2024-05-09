package part5infra

import akka.actor.{Actor, ActorLogging, ActorSystem, PoisonPill, Props}
import akka.dispatch.{ControlMessage, PriorityGenerator, UnboundedPriorityMailbox}
import com.typesafe.config.{Config, ConfigFactory}

object Mailboxes extends App {
  val system = ActorSystem("MailboxDemo", ConfigFactory.load().getConfig("mailboxesDemo"))
  class SimpleActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(message.toString)
    }
  }

  /**
    * Interesting case #1 - custom priority mailbox
    * because normally mailboxes just enqueue every single message that get sent to an actor in regular queue and we would like to add some priority to this message queue and the use case here is a support ticketing system
    * because where it used to work we hacked a little script to auto prioritize support tickets based on the ticket name.
    * So if the ticket name started with P0 then it's the most important and need to be triaged first
    * And then in decreasing priority P1, P2 and P3
    */
  // step 1 - mailbox definition
  class SupportTicketPriorityMailbox(settings: ActorSystem.Settings, config: Config) // at runtime it's instantiated by reflection
      extends UnboundedPriorityMailbox(PriorityGenerator {
        case message: String if message.startsWith("[P0]") => 0
        case message: String if message.startsWith("[P1]") => 1
        case message: String if message.startsWith("[P2]") => 2
        case message: String if message.startsWith("[P3]") => 3
        case _                                             => 4
      })

  // step2 - make it known in the config
  // step3 - attach the dispatcher to an actor

  val supportTicketLogger = system.actorOf(Props[SimpleActor].withDispatcher("support-ticket-dispatcher"))
  supportTicketLogger ! PoisonPill // 그래도 순서대로 실행 됨
  Thread.sleep(1000) //
  supportTicketLogger ! "[P3] this thing would be nice to have"
  supportTicketLogger ! "[P0] this needs to be solved NOW!"
  supportTicketLogger ! "[P1] do this when you have the time"

  // after which time can I send another and be prioritized accordingly? how log is the wait the answer is that neither can you know nor can you configure the weight because when a thread is allocated to dequeue messages from this actor whatever is put on the queue in that particular order which is order by the mailbox will get handled.
  // Thread.sleep(1000) - 메인 스레드가 1초 동안 일시 중지합니다. 이 시간 동안 액터 시스템 내부적으로는 별다른 변화가 없으며, 액터는 PoisonPill 메시지를 아직 처리하지 않습니다.
  // 메시지 전송 - "이것은 좋은 것이 될 것입니다[P3]", "지금 해결해야 합니다[P0]", "시간이 있을 때 이것을 하세요[P1]" 메시지가 순서대로 메일박스에 추가됩니다.
  // Akka의 메일박스는 메시지를 받으면 설정된 우선 순위에 따라 정렬합니다. 이 경우, 우선 순위 메일박스(SupportTicketPriorityMailbox)가 사용되었기 때문에 메시지는 다음 우선순위에 따라 정렬되어 처리됩니다:
  // 따라서 Thread.sleep 이후에 보내진 메시지들은 [P0], [P1], [P3] 순으로 처리됩니다. PoisonPill은 일반적인 메시지 처리 후 종료 시그널로 작용하며, 이 경우 우선 순위에 따라 처리되기 전에 종료되지 않는 이상, 남은 메시지들은 모두 정상적으로 처리됩니다.

  /**
   * Interesting case #2 - control aware mailbox
   * some messages need to be processed first regardless of what's been queued up in the mailbox for example a management ticket for out ticketingSystem that we saw above
   * and for this one we don't need to implement a custom mailbox we'll just use the unbounded control aware mailbox
   * so we'll use UnboundedControlAwareMailbox - unbounded means it theoretically has no bounds, bounded means it only can store like millions of messages
   */
  // step 1 - mark important message as control messages
  case object ManagementTicket extends ControlMessage // Akka에서 ControlMessage는 특별한 유형의 메시지로 정의되며, 일반 메시지보다 높은 우선순위를 가집니다. 이를 사용하는 주된 목적은 시스템이나 네트워크 제어와 관련된 중요한 작업을 처리하는 데 있습니다. 예를 들어, PoisonPill과 같은 시스템 제어 메시지도 ControlMessage의 일종입니다. ControlMessage는 메시지 큐에서 일반 메시지보다 우선 처리되어야 할 때 사용됩니다. 이는 시스템이 긴급하게 반응해야 하는 상황에서 유용하며, 액터가 다량의 메시지를 처리 중일 때도 중요한 제어 메시지를 빠르게 인식하고 반응할 수 있도록 돕습니다. 시스템 모니터링, 긴급 수정, 중요한 상태 업데이트 등의 작업을 ManagementTicket을 통해 처리할 수 있습니다.

  // step 2 - configure who get the mailbox - make the actor attach to the mailbox
  // method #1
  val controlAwareActor = system.actorOf(Props[SimpleActor].withMailbox("control-mailbox"))
//  controlAwareActor ! "[P0] this needs to be solved NOW!"
//  controlAwareActor ! "[P1] do this when you have the time"
//  controlAwareActor ! ManagementTicket

  // method #2 - using deployment config
  val altControlWareActor = system.actorOf(Props[SimpleActor], "altControlWareActor")
  altControlWareActor ! "[P0] this needs to be solved NOW!"
  altControlWareActor ! "[P1] do this when you have the time"
  altControlWareActor ! ManagementTicket

}
