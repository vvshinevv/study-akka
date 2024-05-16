package part6patterns

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.pattern.{ask, pipe}
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Success
import scala.util.Failure

class AskSpec extends TestKit(ActorSystem("AskSpec")) with ImplicitSender with WordSpecLike with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

}

object AskSpec {

  case class Read(key: String)
  case class Write(key: String, value: String)

  class KVActor extends Actor with ActorLogging {
    override def receive: Receive = online(Map())

    def online(kv: Map[String, String]): Receive = {
      case Read(key) =>
        log.info(s"Trying to read the value at the key $key")
        sender() ! kv.get(key) // Option[String]

      case Write(key, value) =>
        log.info(s"Writing the value $value for the $key")
        context.become(online(kv + (key -> value)))
    }
  }

  // use authenticator actor
  case class RegisterUser(username: String, password: String)
  case class Authenticate(username: String, password: String)
  case class AuthFailure(message: String)
  case object AuthSuccess

  class AuthManager extends Actor with ActorLogging {

    implicit val timeout: Timeout = Timeout(1 seconds)
    implicit val ec: ExecutionContext = context.dispatcher

    val authDB = context.actorOf(Props[KVActor])
    override def receive: Receive = {
      case RegisterUser(username, password) => authDB ! Write(username, password)
      case Authenticate(username, password) => handleAuthentication(username, password)
    }

    def handleAuthentication(username: String, password: String) = {
      val originalSender = sender()
      val future = authDB ? Read(username) // runs on a separate thread

      // onComplete runs on another separate thead. Actually It might be same thread that is used for handling the message. It might be a different thread.
      // but regardless, who is the sender of the message when the future has completed?
      // It probably should be the AuthDb not the one who tried to authenticate.
      // And this case is simple enough, because sender returns the actorRef who last send the message.
      // But what if I had accessed a mutable state or called some other method inside this actor in the Future.onComplete callback?
      // Then that would case a race condition between threads.
      // This breaks the actor encapsulation
      // === 아래 해석 ==
      // onComplete는 별도의 스레드에서 실행됨:
      // Future.onComplete 콜백은 비동기적으로 실행되므로, 다른 스레드에서 실행될 수 있습니다. 이는 메시지를 처리하는 스레드와 동일할 수도 있지만, 그렇지 않을 수도 있습니다.

      // Future 완료 시 발신자 (sender) 문제:
      // Future가 완료된 시점에서 메시지를 보내는 발신자(sender)는 원래 메시지를 보낸 Actor일 수도 있고, 그렇지 않을 수도 있습니다. sender는 마지막으로 메시지를 보낸 Actor의 actorRef를 반환합니다.

      // 경쟁 조건과 Actor 캡슐화:
      // Future.onComplete 콜백 내에서 Actor의 가변 상태에 접근하거나 다른 메서드를 호출하는 경우, 스레드 간의 경쟁 조건이 발생할 수 있습니다. 이는 여러 스레드가 동시에 Actor의 상태를 변경하려고 시도할 때 발생하는 문제입니다.

      // 이런 상황은 Actor의 캡슐화 원칙을 깨뜨리며, Actor 모델의 중요한 장점인 스레드 안전성을 해칩니다.

      // so Never in your life call method on  the actor instance or access mutable state in onComplete.
      // avoid closing over the actor instance or mutable state => Future나 Promise와 같은 비동기 코드 내에서 Actor 인스턴스나 가변 상태를 참조하지 않도록 주의해야 한다는 의미입니다. 이 지침을 따르면 Actor 모델의 스레드 안전성과 캡슐화를 유지할 수 있습니다.
      future.onComplete {
        case Success(None) => originalSender ! AuthFailure("password not found")
        case Success(Some(dbPassword)) =>
          if (dbPassword == password) originalSender ! AuthSuccess
          else originalSender ! AuthFailure("password incorrect")
        case Failure(_) => originalSender ! AuthFailure("system error")
      }
    }
  }

  class PipedAuthManager extends AuthManager {
    override def handleAuthentication(username: String, password: String): Unit = {
      val future = authDB ? Read(username)
      val passwordFuture = future.mapTo[Option[String]] // Future[Option[String]]
      val responseFuture = passwordFuture.map {
        case None => AuthFailure("password not found")
        case Some(dbPassword) =>
          if (dbPassword == password) AuthSuccess
          else AuthFailure("system error")
      }

      // when the future completes, send the response to the actor ref in the arg list.
      responseFuture.pipeTo(sender())
    }
  }
}
