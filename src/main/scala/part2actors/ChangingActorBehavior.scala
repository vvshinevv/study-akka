package part2actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object ChangingActorBehavior extends App {

  private object FussyKid {
    case object KidAccept
    case object KidReject
    private val HAPPY = "happy"
    private val SAD = "sad"
  }
  class FussyKid extends Actor {
    import FussyKid._
    import Mom._

    // internal state of the kid
    private var state: String = HAPPY

    override def receive: Receive = {
      case Food(VEGETABLE) => state = SAD
      case Food(CHOCOLATE) => state = HAPPY
      case Ask(_) =>
        if (state == HAPPY) sender() ! KidAccept
        else sender() ! KidReject
    }
  }

  class StatelessFussyKid extends Actor {
    import FussyKid._
    import Mom._

    override def receive: Receive = {
      println("Kid receive")
      happyReceive
    }

    private def happyReceive: Receive = {
      case Food(VEGETABLE) => context.become(sadReceive, false) // change my receive handler to sadReceive , true means discard the old message handler so fully replace the old message handler with a new message handler.
      case Food(CHOCOLATE) => //
      case Ask(_)          => sender() ! KidAccept
    }
    private def sadReceive: Receive = {
      case Food(VEGETABLE) => context.become(sadReceive, false)// stay sad
      case Food(CHOCOLATE) => context.unbecome() // change my receive handler to happyReceive, false means instead of replacing or discarding the old message handler we will simply stack the new message handler onto a stack of message handler.
      case Ask(_)          => sender() ! KidReject
    }
  }

  object Mom {
    case class MomStart(kidRef: ActorRef)
    case class Food(food: String)
    case class Ask(message: String) // do you want to play?
    val VEGETABLE = "veggies"
    val CHOCOLATE = "chocolate"
  }

  class Mom extends Actor {
    import FussyKid._
    import Mom._

    override def receive: Receive = {
      println("Mom receive")
      handler
    }

    private def handler: Receive = {
      case MomStart(kidRef) =>
        // test our interaction
        kidRef ! Food(VEGETABLE)
        kidRef ! Food(VEGETABLE)
        kidRef ! Food(CHOCOLATE)
        kidRef ! Ask("do you want to play?")
      case KidAccept => println("Yah, my kid is happy")
      case KidReject => println("kid is sad.")
    }
  }

  import Mom._

  val system = ActorSystem("changingActorBehaviorDemo")
  private val fussyKid = system.actorOf(Props[FussyKid])
  private val statelessFussyKid = system.actorOf(Props[StatelessFussyKid])
  private val mom = system.actorOf(Props[Mom])


  mom ! MomStart(statelessFussyKid)



  /**
  * mom receives MomStart
  *  kid receives Food(veg) -> kid will change handler to sadReceive
  *  kid receives Ask(play?)
  * mom receives KidReject
  */

}

// 2가지 때문에 좋지 않은 예시
// first of all we often find ourselves in the situation where we need to offer different kinds of behavior depending on the state of this actor.
// now this is a simple variable but in practice the state of the actors might be incredibly complex and so our logic here for handling messages might blow up to hundreds of lines
// secondly, I should be utterly shamed by using variables here in this actor state we would like something that's less mutable.

// context.become
// the receive method that's being called by aka initially returns the happy receive object. so this guy this handler will be used for all future messages.
// but in the case that this actor receives the food vegetable message then context become sad receive basically force aka to swap this handler with this new handler.
// so this guy will then be used for all future messages


//
// Food(veg) -> message handler turns to sadReceive  => stack.push(sadReceive)
// Food(chocolate) -> become happyReceive => stack.push(happyReceive)
// receive Food(veg), Food(chocolate) message in order this is how its message handling stack would look like
// Stack:
// 1. happyReceive
// 2. sadReceive
// 3. happyReceive
// if the stack happens to be empty, then Akka will call the plain receive method.
// Now in order to pop an element out and go back to the old receive handler, we use context.unbecome

// unbecome 부분
// Food(veg)
// Food(veg)
// Food(chocolate)
// Food(chocolate)

// Stack:
// 1. sadReceive
// 2. happyReceive

// Stack:
// 1. sadReceive
// 2. sadReceive
// 3. happyReceive

// Stack:
// 1. sadReceive
// 2. happyReceive

// Stack:
// 1. happyReceive