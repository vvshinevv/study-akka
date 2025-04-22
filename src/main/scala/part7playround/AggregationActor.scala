package part7playround

import akka.actor.{Actor, ActorRef, OneForOneStrategy, Props, SupervisorStrategy, Terminated}
import part7playround.CountReadActor.ReadCount

class AggregationActor(target: String) extends Actor {

  import AggregationActor._

  override val supervisorStrategy: OneForOneStrategy = OneForOneStrategy(maxNrOfRetries = 2) {
    case _: Throwable => SupervisorStrategy.Restart
  }

  override def receive: Receive = {
    case Start =>
      val senderRef = context.sender()
      val dates = Seq("20250420", "20250421", "20250422")
      val children = dates.foldLeft(Set[ActorRef]()) { (acc, date) =>
        val child = context.actorOf(Props(new CountReadActor(date)))
        context.watch(child)
        child ! ReadCount
        acc + child
      }
      context.become(doAggregate(senderRef, Map(), children))
  }

  private def doAggregate(senderRef: ActorRef, aggregateMap: Map[String, Int], children: Set[ActorRef]): Receive = {
    case ReadSuccess(date, count) =>
      val updatedAggregateMap = aggregateMap + (date -> count)
      context.become(doAggregate(senderRef, updatedAggregateMap, children))
    case Terminated(child) =>
      val updatedChildren = children - child
      if (updatedChildren.isEmpty) {
        senderRef ! AggregateSummary(target, aggregateMap)
        context.stop(self)
      } else {
        context.become(doAggregate(senderRef, aggregateMap, updatedChildren))
      }
  }
}

object AggregationActor {
  case object Start
  case class ReadSuccess(date: String, count: Int)
  case class AggregateSummary(target: String, aggregateDateMap: Map[String, Int])
}
