package csw.common.components

import akka.typed.ActorRef
import akka.typed.scaladsl.ActorContext
import csw.common.ccs.{Validation, Validations}
import csw.common.framework.models.PubSub.{Publish, PublisherMessage}
import csw.common.framework.models.{CommandMessage, ComponentInfo, ComponentMessage}
import csw.common.framework.scaladsl.ComponentHandlers
import csw.param.generics.GChoiceKey
import csw.param.generics.KeyType.ChoiceKey
import csw.param.models.{Choice, Choices, Prefix}
import csw.param.states.CurrentState

import scala.concurrent.Future

object SampleComponentState {
  val restartChoice  = Choice("Restart")
  val runChoice      = Choice("Run")
  val onlineChoice   = Choice("Online")
  val domainChoice   = Choice("Domain")
  val shutdownChoice = Choice("Shutdown")
  val commandChoice  = Choice("Command")
  val initChoice     = Choice("Initialize")
  val offlineChoice  = Choice("Offline")
  val prefix: Prefix = Prefix("wfos.prog.cloudcover")

  val choices: Choices =
    Choices.fromChoices(restartChoice,
                        runChoice,
                        onlineChoice,
                        domainChoice,
                        shutdownChoice,
                        commandChoice,
                        initChoice,
                        offlineChoice)
  val choiceKey: GChoiceKey = ChoiceKey.make("choiceKey", choices)
}

class SampleComponentHandlers(ctx: ActorContext[ComponentMessage],
                              componentInfo: ComponentInfo,
                              pubSubRef: ActorRef[PublisherMessage[CurrentState]])
    extends ComponentHandlers[ComponentDomainMessage](ctx, componentInfo, pubSubRef) {
  import SampleComponentState._
  var testProbe: Option[ActorRef[CurrentState]] = None

  override def onRun(): Future[Unit] =
    Future.successful(pubSubRef ! Publish(CurrentState(prefix, Set(choiceKey.set(runChoice)))))
  override def onGoOnline(): Unit = pubSubRef ! Publish(CurrentState(prefix, Set(choiceKey.set(onlineChoice))))
  override def onDomainMsg(msg: ComponentDomainMessage): Unit = {
    pubSubRef ! Publish(CurrentState(prefix, Set(choiceKey.set(domainChoice))))
    msg match {
      case UpdateTestProbe(replyTo) => testProbe = Some(replyTo)
      case ComponentStatistics(_)   =>
    }
  }
  override def onShutdown(): Future[Unit] = {
    pubSubRef ! Publish(CurrentState(prefix, Set(choiceKey.set(shutdownChoice))))
    testProbe.foreach(_ ! CurrentState(prefix, Set(choiceKey.set(shutdownChoice))))
    Future.unit
  }
  override def onControlCommand(commandMsg: CommandMessage): Validation = {
    pubSubRef ! Publish(CurrentState(prefix, Set(choiceKey.set(commandChoice))))
    Validations.Valid
  }
  override def initialize(): Future[Unit] = {
    pubSubRef ! Publish(CurrentState(prefix, Set(choiceKey.set(initChoice))))
    Thread.sleep(100)
    Future.unit
  }
  override def onGoOffline(): Unit = pubSubRef ! Publish(CurrentState(prefix, Set(choiceKey.set(offlineChoice))))
}
