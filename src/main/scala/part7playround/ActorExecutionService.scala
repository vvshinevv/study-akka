package part7playround

import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import part7playround.AggregationActor.{AggregateSummary, Start}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

object ActorExecutionService extends App {

  implicit val timeout: Timeout = 3.seconds

  val system = ActorSystem("ActorExecutionService")

  val aggregationActor = system.actorOf(Props(new AggregationActor("targetKey")))

  (aggregationActor ? Start)
    .mapTo[AggregateSummary]
    .foreach { result =>
      println(result.target)
      println(result.aggregateDateMap)
    }
}
