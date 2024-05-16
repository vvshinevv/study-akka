package part6patterns

import akka.actor.{Actor, ActorLogging, ActorSystem, Props, Stash}

object StashDemo extends App {

  /**
    * ResourceActor
    *  - open => it can receive read / write request to the resource
    *  - otherwise it will postpone all read / write request until the state is open
    *
    *  ResourceActor is closed
    *   - open => switch to the open state
    *   - Read, Write message are POSTPONE
    *
    *  ResourceActor is open
    *   - Read, Write are handled
    *   - Close => switch to the closed state
    *
    *  [OPEN, READ, READ, WRITE]
    *   - switch to the open state
    *   - read the data
    *   - read the data again
    *   - write teh data
    *
    *  [READ, OPEN, WRITE]
    *   - stash READ
    *     STASH : [READ]
    *   - open => switch to the open state
    *     MAILBOX: [READ, WRITE]
    *   - read and write are handled
    *
    */
  case object Open
  case object Close
  case object Read
  case class Write(data: String)

  // step1 - mix in the stash trait
  class ResourceActor extends Actor with ActorLogging with Stash {
    // stash allows the capability of putting message aside for later and popping them out for prepending the to the mailbox. (stash는 나중을 위해 메시지를 따로 저장하고, 이를 꺼내어 메일함에 앞쪽에 추가할 수 있는 기능을 제공합니다.)

    private var innerData: String = ""
    override def receive: Receive = closed

    def closed: Receive = {
      case Open =>
        log.info("Opening resource")
        // step 3 - unStashAll when you switch the message handler
        unstashAll()
        context.become(open)

      case message =>
        log.info(s"Stashing $message because I can't handle it in the closed state")
        // step 2 - stash away what you can't handle
        stash()
    }

    def open: Receive = {
      case Read =>
        // do some actual computation
        log.info(s"I have read $innerData")
      case Write(data) =>
        log.info(s"I am writing $data")
        innerData = data
      case Close =>
        log.info("Closing resource")
        unstashAll()
        context.become(closed)
      case message =>
        log.info(s"Stashing $message because I can't handle it in the open state")
        // step 2 - stash away what you can't handle
        stash()
    }
  }

  val system = ActorSystem("StashDemo")
  val resourceActor = system.actorOf(Props[ResourceActor])

  resourceActor ! Read
  resourceActor ! Open
  resourceActor ! Open
  resourceActor ! Write("I love stash")
  resourceActor ! Close
  resourceActor ! Read

  // First of all there are potential memory bounds on stash, although this is rarely a problem in practice because stashes are backed by vectors
  // so you can safely store away millions of messages without any significant performance impact.
  // Second, Depending on which mailbox you use, unStashing might pose problems to the bound of your mailbox because if you store away millions of messages and there is no more room in your mailbox, the mailbox will overflow and some messages might end up being dropped.
  // Either way, at the end of unStashing, the stash will be guaranteed to be empty.
  // Then make sure you don't stash the same message twice because it will throw an exception.
  // Make sure you mix in your stash trait last due to trait linearization. - the Stash trait overrides preRestart so must be mixed in last

  // 첫째, stash에는 잠재적인 메모리 한계가 있을 수 있지만, 실질적으로는 드물게 문제가 됩니다. stash는 벡터로 지원되므로 수백만 개의 메시지를 안전하게 저장할 수 있으며 성능에 큰 영향을 주지 않습니다.
  // 둘째, 사용하는 메일박스에 따라 unstashing이 메일박스의 한계에 문제를 일으킬 수 있습니다. 수백만 개의 메시지를 저장하면 메일박스에 더 이상 공간이 없을 수 있으며, 이 경우 메일박스가 오버플로우되어 일부 메시지가 삭제될 수 있습니다. 어쨌든 unstashing이 끝나면 stash는 반드시 비어 있어야 합니다.
  // 그 다음으로, 동일한 메시지를 두 번 stash하지 않도록 주의해야 합니다. 그렇지 않으면 예외가 발생합니다.
  // 마지막으로, trait 선형화 때문에 stash trait를 마지막에 혼합해야 합니다. Stash trait는 preRestart 메서드를 오버라이드하므로 마지막에 혼합되어야 합니다.
}
