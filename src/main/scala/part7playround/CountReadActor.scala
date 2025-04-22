package part7playround

import akka.actor.{Actor, ActorRef}
import akka.pattern.pipe
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import org.reactivestreams.{Publisher, Subscriber, Subscription}
import part7playround.AggregationActor.ReadSuccess

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

class CountReadActor(date: String) extends Actor {

  import CountReadActor._

  implicit val ec: ExecutionContext = context.dispatcher
  implicit val mat: Materializer = Materializer(context.system)

  override def postRestart(reason: Throwable): Unit = {
    reason match {
      case StreamException(selfRef, retryMessage) =>
        context.system.scheduler.scheduleOnce(1.seconds, selfRef, retryMessage)
    }
  }

  override def receive: Receive = {
    case ReadCount =>
      Source
        .fromPublisher(createPublisher(1L to 100L))
        .runWith(Sink.fold(0) { case (count, _) => count + 1 })
        .map(ReadFinish(date, _))
        .recover {
          case _: Throwable => throw StreamException(context.self, date)
        }
        .pipeTo(self)

    case ReadFinish(date, count) =>
      context.parent ! ReadSuccess(date, count)
      context.stop(self)
  }
}

object CountReadActor {
  case object ReadCount
  case class ReadFinish(date: String, count: Int)
  case class StreamException(selfRef: ActorRef, actorMessage: String) extends RuntimeException

  def createPublisher(ids: Seq[Long]): Publisher[Long] = (s: Subscriber[_ >: Long]) => {
    s.onSubscribe(new Subscription {
      override def request(n: Long): Unit = {
        ids.foreach(i => s.onNext(i))
        s.onComplete()
      }

      override def cancel(): Unit = {}
    })
  }
}
