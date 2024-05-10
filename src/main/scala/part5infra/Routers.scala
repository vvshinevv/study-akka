package part5infra

import akka.actor.{Actor, ActorLogging, ActorSystem, Props, Terminated}
import akka.routing._
import com.typesafe.config.ConfigFactory

object Routers extends App {

  /**
    * #1 - manual router
    */
  class Master extends Actor with ActorLogging {
    // I've created five actor Routees based off slave actors and I'm passing these slaves to a router which acts according to a RoundRobinRoutingLogic which basically means that it cycles between all the actors

    // step1 - create routees
    private val slaves: IndexedSeq[ActorRefRoutee] = for (i <- 1 to 5) yield {
      val slave = context.actorOf(Props[Slave], s"Slave$i")
      context.watch(slave)

      ActorRefRoutee(slave)
    }

    // step2 - define router
    private val router = Router(RoundRobinRoutingLogic(), slaves)

    override def receive: Receive = {
      // step3 - handle some messages to these routees ( route the messages )
      // The way that I'm going to do that is invoke the router.
      case message =>
        router.route(message, sender()) // The slave actors can reply directly to the sender without involving me the master.

      // step4 - handle the termination/lifecycle of the routees because I register myself to the death watch of every single slave.
      // So I need to handle their terminated messages.
      case Terminated(ref) =>
        // let's assume for this example that if one of my reference, one of my slaves dies I'll just replace it with another slave.
        // so the way that I'm going to do that is first remove the rootees from the router.
        router.removeRoutee(ref)

        // Natually I need to create a new one.
        val newSlave = context.actorOf(Props[Slave])

        // And then I need to register myself for its death watch.
        context.watch(newSlave)

        // And Then add it back to the routee
        router.addRoutee(newSlave) // This is how I simply replace the terminated slave with a new one.

      // ** The addRoutee and removeRoutee methods return a new router, so make the router a var and reassing it

      // These are the step for defining a router manually.
      // First you need to create the routees based off the actor refs.
      // Second you need to define your router based on supported router logic and we are going to discuss what routing logics are available based off the slaves that you've just created in step one.
      // Third you need to handle the messages so you need to forward that to the router by calling router.route and you can pass in the message and the sender of that message. so that your slaves know who to respond to. You can pass in yourself if you want.
      // if you want to be the middleman but often we pass in sender.
      // Step four we need to handle the lifecycle of our routees by in this case just removing the routee, creating a new one and registering for its death watch and just putting it back in router. //  경우에 루티를 제거하고 새로운 루티를 생성한 다음, 그 루티의 생명 주기를 감시하고 라우터에 다시 추가하는 방식으로 우리의 루티들의 생명 주기를 관리해야 합니다.

      // Supported options for routing logic:
      // - round-robin
      // - random
      // - smallest mailbox: load balancing heuristic it always sends the next message to the actor with a fewest message ing the queue
      // - broadcast: redundancy measure this send the same message to all the routees
      // - scatter-gather-first: broadcasts sends to everyone and waits for the first reply and all the next replies are discarded
      // - tail-chopping: forwards the next message to each actor sequentially until the first replies received and all the other replies are discarded "tail-chopping"은 다음 메시지를 순차적으로 각 액터에게 전달하고, 첫 번째로 수신된 응답을 받은 후에 다른 모든 응답은 무시합니다.
      // - consistent-hashing: all the messages with the same hash get to the same actor
    }
  }

  private class Slave extends Actor with ActorLogging {
    override def receive: Receive = {
      case messages => log.info(messages.toString)
    }
  }

  val system = ActorSystem("RoutersDemo", ConfigFactory.load().getConfig("routersDemo"))
  val master = system.actorOf(Props[Master])

//  for (i <- 1 to 10) {
//    master ! s"[$i] Hello from the world"
//  }

  /**
    * Method #2 - a router actor with its own children and this is called a POOL router
    */
  // 2.1 programmatically (in code)
  val poolMaster = system.actorOf(RoundRobinPool(5).props(Props[Slave]), "simplePoolMaster") // pool means the router actor has its own children
  // So when I say RoundRobinPool(5).props(Props[Slave]) - this actor creates five slaves under itself so if I go ahead and send 10 messages to this pool Master

//  for (i <- 1 to 10) {
//    poolMaster ! s"[$i] Hello from the world"
//  }

  // 2.2 from configuration
  val poolMaster2 = system.actorOf(FromConfig.props(Props[Slave]), "poolMaster2")
//  for (i <- 1 to 10) {
//    poolMaster2 ! s"[$i] Hello from the world"
//  }

  /**
    * Method #3 - router with actors created elsewhere
    * GROUP router
    */
  // .. in another part of my application I create 5 slave actors
  val slaveList = (1 to 5).map(i => system.actorOf(Props[Slave], s"slave_$i")).toList

  // need their actor path
  val slavePaths = slaveList.map(slaveRef => slaveRef.path.toString)

  // 3.1 in the code
  val groupMaster = system.actorOf(RoundRobinGroup(slavePaths).props())
//  for (i <- 1 to 10) {
//    groupMaster ! s"[$i] Hello from the world"
//  }

  // 3.2 configuration
  val groupMaster2 = system.actorOf(FromConfig.props(), "groupMaster2")
  for (i <- 1 to 10) {
    groupMaster2 ! s"[$i] Hello from the world"
  }

  /**
    * Special messages
    */
  groupMaster2 ! Broadcast("hello everyone")

  // PoisonPill and Kill are NOT routed - They are handled by the routing actor
  // There are management messages such
}

/**
 * Routers recap
 * We've learned how to use routers to spread or delegate messages in between a number of identical actor
 * and we've seen a number of way to construct routers the method
 *
 * # Router method 1: manual 라우터 방법 1: 수동
 * one was the most laborious and for the ake of our sanity. - I'm going to ignore it because It's rarely used in practice It may be powerful but It's rarely used. 이 방법은 가장 힘든 작업이며, 실제로 거의 사용되지 않기 때문에 이를 무시하고 넘어가겠습니다. 이 방법은 강력할 수 있지만 실제로는 거의 사용되지 않습니다.
 *
 * # Router method 2: pool routers 라우터 방법 2: 풀 라우터(Pool Routers)
 * router can create child actors in itself 라우터는 자체적으로 자식 액터를 생성할 수 있습니다.
 * >> val router = system.actorOf(RoundRobinPool(5).props(Props[MyActor]), "myRouter")
 * >> val router = system.actorOf(FromConfig.props(Props[MyActor]), "myPoolRouter")
 *
 * # Router method 3: group routers 라우터 방법 3: 그룹 라우터(Group Routers)
 * group routers which are very useful when your routines are created elsewhere in your application 그룹 라우터는 애플리케이션의 다른 곳에서 루틴이 생성될 때 매우 유용합니다. 이 경우에는 액터 경로만 필요합니다. "그룹 라우터는 애플리케이션의 다른 곳에서 루틴이 생성될 때 매우 유용합니다"라는 문장은, 애플리케이션의 여러 부분에서 이미 생성되어 있는 액터들에게 메시지를 라우팅할 때 그룹 라우터를 효과적으로 사용할 수 있다는 것을 의미합니다. 즉, 라우터가 직접 자식 액터들을 생성하는 것이 아니라, 이미 존재하는 액터들의 경로(주소)를 이용하여 그 액터들로 구성된 그룹에 메시지를 분배합니다. 이러한 방식은 특히 대규모 분산 시스템이나 마이크로서비스 아키텍처에서 유용하게 사용될 수 있습니다. 애플리케이션의 다양한 서비스나 컴포넌트에서 이미 생성되어 운영 중인 액터들이 있을 때, 그룹 라우터를 사용하면 이러한 액터들에게 메시지를 효율적으로 라우팅하고 로드 밸런싱 할 수 있습니다. 예를 들어, 여러 서비스에서 각각의 액터를 관리하고 있고, 이 액터들이 특정 작업을 수행해야 할 경우, 그룹 라우터는 이들 액터의 경로를 리스트 형태로 받아 그룹을 형성하고, 필요에 따라 각 액터에게 메시지를 분배하여 작업을 처리하게 합니다. 이 방식을 통해 개별 액터들을 중앙에서 관리할 필요 없이 효율적인 메시지 처리가 가능합니다.
 * in that case you only need your actor paths
 * >> val router = system.actorOf(RoundRobinGroup(paths), "myRouter")
 * >> val router = system.actorOf(FromConfig.props(Props[MyActor]), "myGroupRouter")
 *
 * How some message are handled specially in particular broadcast
 * Special Message: BroadCast PoisonPill and Routee related messages. 특정 메시지는 특별히 다루어집니다. 예를 들어, 브로드캐스트, PoisonPill, 라우티 관련 메시지 등이 그 예입니다.
 *
 * 라우터에 PoisonPill 이 보내지면, 이 메시지는 라우티들에게 방송되어 각 라우티를 순차적으로 종료시킵니다.
 *
 * 라우티 관련 메시지: 라우터를 통해 라우티들에게 메시지를 보낼 때, 메시지는 라우터의 라우팅 로직에 따라 하나 또는 여러 라우티에게 전달됩니다. 라우터의 구성에 따라 라우티들에게 메시지를 분산시키거나 특정 라우티에게 집중적으로 메시지를 보낼 수 있습니다.
 */