package csw.common.framework.internal.supervisor

import akka.Done
import akka.actor.CoordinatedShutdown
import akka.typed.scaladsl.adapter.TypedActorSystemOps
import akka.typed.scaladsl.{Actor, ActorContext, TimerScheduler}
import akka.typed.{ActorRef, Behavior, PostStop, Signal, SupervisorStrategy, Terminated}
import csw.common.framework.exceptions.FailureRestart
import csw.common.framework.internal.pubsub.PubSubBehaviorFactory
import csw.common.framework.internal.supervisor.SupervisorLifecycleState.Idle
import csw.common.framework.models.FromComponentLifecycleMessage.Running
import csw.common.framework.models.FromSupervisorMessage.SupervisorLifecycleStateChanged
import csw.common.framework.models.PubSub.Publish
import csw.common.framework.models.RunningMessage.Lifecycle
import csw.common.framework.models.SupervisorCommonMessage.{
  ComponentStateSubscription,
  GetSupervisorLifecycleState,
  LifecycleStateSubscription
}
import csw.common.framework.models.SupervisorIdleMessage._
import csw.common.framework.models.SupervisorRestartMessage.{UnRegistrationComplete, UnRegistrationFailed}
import csw.common.framework.models.ToComponentLifecycleMessage.{GoOffline, GoOnline}
import csw.common.framework.models._
import csw.common.framework.scaladsl.ComponentBehaviorFactory
import csw.param.states.CurrentState
import csw.services.location.models.Connection.AkkaConnection
import csw.services.location.models.{AkkaRegistration, ComponentId, RegistrationResult}
import csw.services.location.scaladsl.{LocationService, RegistrationFactory}
import csw.services.logging.scaladsl.ComponentLogger

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.{DurationDouble, FiniteDuration}
import scala.util.{Failure, Success}

object SupervisorBehavior {
  val PubSubComponentActor              = "pub-sub-component"
  val ComponentActor                    = "component"
  val PubSubLifecycleActor              = "pub-sub-lifecycle"
  val InitializeTimerKey                = "initialize-timer"
  val initializeTimeout: FiniteDuration = 5.seconds
}

class SupervisorBehavior(
    ctx: ActorContext[SupervisorMessage],
    timerScheduler: TimerScheduler[SupervisorMessage],
    maybeContainerRef: Option[ActorRef[ContainerIdleMessage]],
    componentInfo: ComponentInfo,
    componentBehaviorFactory: ComponentBehaviorFactory[_],
    pubSubBehaviorFactory: PubSubBehaviorFactory,
    registrationFactory: RegistrationFactory,
    locationService: LocationService
) extends ComponentLogger.TypedActor[SupervisorMessage](ctx, componentInfo.name) {

  import SupervisorBehavior._

  implicit val ec: ExecutionContext = ctx.executionContext

  val componentName: String                              = componentInfo.name
  val componentId                                        = ComponentId(componentName, componentInfo.componentType)
  val akkaRegistration: AkkaRegistration                 = registrationFactory.akkaTyped(AkkaConnection(componentId), ctx.self)
  var haltingFlag                                        = false
  var lifecycleState: SupervisorLifecycleState           = Idle
  var runningComponent: Option[ActorRef[RunningMessage]] = None
  var registrationOpt: Option[RegistrationResult]        = None
  var component: ActorRef[Nothing]                       = _

  val pubSubLifecycle: ActorRef[PubSub[LifecycleStateChanged]] =
    pubSubBehaviorFactory.make(ctx, PubSubLifecycleActor, componentName)
  val pubSubComponent: ActorRef[PubSub[CurrentState]] =
    pubSubBehaviorFactory.make(ctx, PubSubComponentActor, componentName)

  registerWithLocationService()

  override def onMessage(msg: SupervisorMessage): Behavior[SupervisorMessage] = {
    log.debug(s"Supervisor in lifecycle state :[$lifecycleState] received message :[$msg]")
    (lifecycleState, msg) match {
      case (_, msg: SupervisorCommonMessage)                           ⇒ onCommon(msg)
      case (SupervisorLifecycleState.Idle, msg: SupervisorIdleMessage) ⇒ onIdle(msg)
      case (SupervisorLifecycleState.Running | SupervisorLifecycleState.RunningOffline, msg: SupervisorRunningMessage) ⇒
        onRunning(msg)
      case (SupervisorLifecycleState.Restart, msg: SupervisorRestartMessage) ⇒ onRestarting(msg)
      case (_, message) =>
        log.error(s"Unexpected message :[$message] received by supervisor in lifecycle state :[$lifecycleState]")
    }
    this
  }

  override def onSignal: PartialFunction[Signal, Behavior[SupervisorMessage]] = {
    case Terminated(componentRef) ⇒
      timerScheduler.cancel(InitializeTimerKey)
      lifecycleState match {
        case SupervisorLifecycleState.Restart ⇒
          log.warn(
            s"Supervisor in lifecycle state :[$lifecycleState] received terminated signal from component :[$componentRef]"
          )
          lifecycleState = SupervisorLifecycleState.Idle
          registerWithLocationService()
        case SupervisorLifecycleState.Shutdown ⇒
          log.warn(
            s"Supervisor in lifecycle state :[$lifecycleState] received terminated signal from component :[$componentRef]"
          )
          coordinatedShutdown()
        case _ ⇒
          log.error(
            s"Supervisor in lifecycle state :[$lifecycleState] received unexpected terminated signal from component :[$componentRef]"
          )
      }
      this
    case PostStop ⇒
      log.warn(s"Supervisor is shutting down. Un-registering supervisor from location service")
      registrationOpt.foreach(registrationResult ⇒ registrationResult.unregister())
      this
  }

  def onCommon(msg: SupervisorCommonMessage): Unit = msg match {
    case LifecycleStateSubscription(subscriberMessage) ⇒ pubSubLifecycle ! subscriberMessage
    case ComponentStateSubscription(subscriberMessage) ⇒ pubSubComponent ! subscriberMessage
    case GetSupervisorLifecycleState(replyTo)          ⇒ replyTo ! lifecycleState
    case Restart                                       ⇒ onRestart()
    case Shutdown ⇒
      log.debug(
        s"Supervisor is changing lifecycle state from [$lifecycleState] to [${SupervisorLifecycleState.Shutdown}]"
      )
      lifecycleState = SupervisorLifecycleState.Shutdown
      ctx.child(ComponentActor) match {
        case Some(componentRef) ⇒ ctx.stop(componentRef)
        case None               ⇒ coordinatedShutdown()
      }
  }

  def onIdle(msg: SupervisorIdleMessage): Unit = msg match {
    case RegistrationComplete(registrationResult) ⇒
      registrationOpt = Some(registrationResult)
      spawnAndWatchComponent()
    case RegistrationFailed(throwable) ⇒
      log.error(throwable.getMessage, ex = throwable)
      throw throwable
    case Running(componentRef) ⇒
      log.debug(
        s"Supervisor is changing lifecycle state from [$lifecycleState] to [${SupervisorLifecycleState.Running}]"
      )
      lifecycleState = SupervisorLifecycleState.Running
      timerScheduler.cancel(InitializeTimerKey)
      runningComponent = Some(componentRef)
      maybeContainerRef foreach { container ⇒
        container ! SupervisorLifecycleStateChanged(ctx.self, lifecycleState)
        log.debug(s"Supervisor acknowledged container :[$container] for lifecycle state :[$lifecycleState]")
      }
      pubSubLifecycle ! Publish(LifecycleStateChanged(ctx.self, SupervisorLifecycleState.Running))
    case InitializeTimeout ⇒
      log.error("Component initialization timed out")
  }

  private def onRunning(supervisorRunningMessage: SupervisorRunningMessage): Unit = {
    supervisorRunningMessage match {
      case runningMessage: RunningMessage ⇒
        runningMessage match {
          case Lifecycle(message) ⇒ onLifeCycle(message)
          case _                  ⇒
        }
        runningComponent.get ! runningMessage
    }
  }

  private def onRestart(): Unit = {
    log.debug(s"Supervisor is changing lifecycle state from [$lifecycleState] to [${SupervisorLifecycleState.Restart}]")
    lifecycleState = SupervisorLifecycleState.Restart
    registrationOpt match {
      case Some(registrationResult) ⇒
        unRegisterFromLocationService(registrationResult)
      case None ⇒
        log.warn("No valid RegistrationResult found to unregister")
        respawnComponent()
    }
  }

  private def onRestarting(msg: SupervisorRestartMessage): Unit = msg match {
    case UnRegistrationComplete ⇒
      log.info("Supervisor unregistered itself from location service")
      respawnComponent()
    case UnRegistrationFailed(throwable) ⇒
      log.error(throwable.getMessage, ex = throwable)
      respawnComponent()
  }

  private def respawnComponent(): Unit = {
    log.info("Supervisor re-spawning component")
    ctx.child(ComponentActor) match {
      case Some(componentRef) ⇒
        ctx.stop(component)
      case None ⇒
        log.debug(
          s"Supervisor is changing lifecycle state from [$lifecycleState] to [${SupervisorLifecycleState.Idle}]"
        )
        lifecycleState = SupervisorLifecycleState.Idle
        registerWithLocationService()
    }
  }

  private def onLifeCycle(message: ToComponentLifecycleMessage): Unit = {
    message match {
      case GoOffline ⇒
        if (lifecycleState == SupervisorLifecycleState.Running) {
          log.debug(
            s"Supervisor is changing lifecycle state from [$lifecycleState] to [${SupervisorLifecycleState.RunningOffline}]"
          )
          lifecycleState = SupervisorLifecycleState.RunningOffline
        }
      case GoOnline ⇒
        if (lifecycleState == SupervisorLifecycleState.RunningOffline) {
          log.debug(
            s"Supervisor is changing lifecycle state from [$lifecycleState] to [${SupervisorLifecycleState.Running}]"
          )
          lifecycleState = SupervisorLifecycleState.Running
        }
    }
  }

  private def registerWithLocationService(): Unit = {
    log.debug(
      s"Supervisor with connection :[${akkaRegistration.connection.name}] is registering with location service with ref :[${akkaRegistration.actorRef}]"
    )
    locationService.register(akkaRegistration).onComplete {
      case Success(registrationResult) ⇒ ctx.self ! RegistrationComplete(registrationResult)
      case Failure(throwable)          ⇒ ctx.self ! RegistrationFailed(throwable)
    }
  }

  private def unRegisterFromLocationService(registrationResult: RegistrationResult): Unit = {
    log.debug(s"Un-registering supervisor from location service")
    registrationResult.unregister().onComplete {
      case Success(_)         ⇒ ctx.self ! UnRegistrationComplete
      case Failure(throwable) ⇒ ctx.self ! UnRegistrationFailed(throwable)
    }
  }

  private def spawnAndWatchComponent(): Unit = {
    log.debug(s"Supervisor is spawning component TLA")
    component = ctx.spawn[Nothing](
      Actor
        .supervise[Nothing](componentBehaviorFactory.make(componentInfo, ctx.self, pubSubComponent, locationService))
        .onFailure[FailureRestart](SupervisorStrategy.restart.withLoggingEnabled(true)),
      ComponentActor
    )
    ctx.watch(component)
    timerScheduler.startSingleTimer(InitializeTimerKey, InitializeTimeout, initializeTimeout)
  }

  private def coordinatedShutdown(): Future[Done] = CoordinatedShutdown(ctx.system.toUntyped).run()
}
