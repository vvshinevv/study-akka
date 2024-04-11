package part4faulttolaerance

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.pattern.{BackoffSupervisor, _}

import java.io.File
import scala.io.Source

/**
  * The BackoffSupervisorPattern wants to solve a big pain that we often see in practice, which is teh repeated restarts of actors.
  * Especially in the context of the actors interacting with external resources, for example a database.
  * If a database goes down and many actors are trying to read or write at that database, many actors will start to throw exceptions which will start supervision strategies.
  * And If the actors start at the same time, the database might go down again or the actors might get into a blocking state.
  *
  * BackoffSupervisorPattern introduces exponential delays as well as randomness in between the attempts to rerun a supervision strategy.
  */
object BackoffSupervisorPattern extends App {

  case object ReadFile
  class FileBasedPersistentActor extends Actor with ActorLogging {
    var dataSource: Source = null

    override def preStart(): Unit = log.info("Persistent actor starting")

    override def postStop(): Unit = log.warning("Persistent actor has stopped")

    override def preRestart(reason: Throwable, message: Option[Any]): Unit = log.warning("Persistent actor restarting")

    override def receive: Receive = {
      case ReadFile =>
        if (dataSource == null)
          dataSource = Source.fromFile(new File("src/main/resources/testfiles/important.txt"))

        log.info("I've just read som IMPORTANT data: " + dataSource.getLines().toList)
    }
  }

  val system = ActorSystem("BackoffSupervisorPattern")
  val simpleActor = system.actorOf(Props[FileBasedPersistentActor], "simpleActor")
  simpleActor ! ReadFile

  private val simpleSupervisorProps =
    BackoffSupervisor.props(
      BackoffOpts.onFailure(
        Props[FileBasedPersistentActor],
        "simpleBackoffActor",
        java.time.Duration.ofSeconds(3),
        java.time.Duration.ofSeconds(20),
        0.2
      )
    )

  val simpleBackoffSupervisor = system.actorOf(simpleSupervisorProps)
  simpleBackoffSupervisor ! ReadFile
  /**
  * simpleSupervisor
  *  - When I create this actor the simpleSupervisor actually creates a child called simpleBackoffActor which is based out of the props of type fileBasedPersistentActor.
   * - so I have a fileBasedPersistentActor under this simpleSupervisor parent.
   * - So basically I've created two actors one a parent and one a child of type fileBasedPersistentActor.
   * - And the simpleSupervisor can receive any message and it will forward them to its child.
   * - Now supervision strategy is the default one which is basically restarting on everything
   *
   * ㄴ first attempt it kicks in after three seconds
   * ㄴ If the child actor fails again the next attempts is 2x or double the previous attempts.
   * ㄴ So the attempts will be after 3 -> 6 -> 24 because the cap is at 30 seconds per backoff
   * ㄴ And the randomness factor is 0.2 which add a little bit of noise to this time so that we don't have a hug amount of actors starting off at that exact moment.
   *
  */
}
