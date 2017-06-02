package csw.services.location

import akka.Done
import akka.actor.{Actor, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import csw.services.location.ClusterConfirmationActor.{HasJoinedCluster, IsMemberUp}

class ClusterConfirmationActor extends Actor {

  val cluster = Cluster(context.system)

  override def preStart(): Unit = cluster.subscribe(self, InitialStateAsEvents, classOf[MemberEvent])
  override def postStop(): Unit = cluster.unsubscribe(self)

  var done: Option[Done] = None
  var up: Option[Done]   = None

  override def receive: Receive = {
    case MemberUp(member) if member.address == cluster.selfAddress       ⇒ done = Some(Done); up = Some(Done)
    case MemberWeaklyUp(member) if member.address == cluster.selfAddress ⇒ done = Some(Done)
    case HasJoinedCluster                                                ⇒ sender() ! done
    case IsMemberUp                                                      ⇒ sender() ! up
  }

}

object ClusterConfirmationActor {
  def props() = Props(new ClusterConfirmationActor)

  case object HasJoinedCluster
  case object IsMemberUp
}
