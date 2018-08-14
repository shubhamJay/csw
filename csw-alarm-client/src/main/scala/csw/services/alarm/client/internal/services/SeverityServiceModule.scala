package csw.services.alarm.client.internal.services

import acyclic.skipped
import akka.actor.ActorSystem
import akka.actor.typed.ActorRef
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, Materializer}
import csw.services.alarm.api.exceptions.{InactiveAlarmException, InvalidSeverityException, KeyNotFoundException}
import csw.services.alarm.api.internal.{SeverityKey, SeverityService}
import csw.services.alarm.api.models.AlarmSeverity.Disconnected
import csw.services.alarm.api.models.Key.AlarmKey
import csw.services.alarm.api.models.{AlarmSeverity, Key}
import csw.services.alarm.api.scaladsl.AlarmSubscription
import csw.services.alarm.client.internal.commons.Settings
import csw.services.alarm.client.internal.redis.RedisConnectionsFactory
import csw.services.alarm.client.internal.{AlarmCodec, AlarmServiceLogger}
import reactor.core.publisher.FluxSink.OverflowStrategy

import scala.async.Async.{async, await}
import scala.concurrent.Future

trait SeverityServiceModule extends SeverityService {
  self: MetadataServiceModule with StatusServiceModule ⇒

  val redisConnectionsFactory: RedisConnectionsFactory
  def settings: Settings
  implicit val actorSystem: ActorSystem
  import redisConnectionsFactory._

  private val log = AlarmServiceLogger.getLogger

  private implicit lazy val mat: Materializer = ActorMaterializer()

  final override def setSeverity(key: AlarmKey, severity: AlarmSeverity): Future[Unit] = async {
    require(severity != Disconnected, "setting severity to Disconnected explicitly is not allowed")
    await(setCurrentSeverity(key, severity))
    await(updateStatusForSeverity(key, severity))
  }

  final override def getAggregatedSeverity(key: Key): Future[AlarmSeverity] = async {
    log.debug(s"Get aggregated severity for alarm [${key.value}]")
    val metadataApi = await(metadataApiF)
    val severityApi = await(severityApiF)

    val metadataKeys = await(metadataApi.keys(key))
    if (metadataKeys.isEmpty) logAndThrow(KeyNotFoundException(key))

    val metadataList = await(metadataApi.mget(metadataKeys)).filter(_.getValue.isActive)
    if (metadataList.isEmpty) logAndThrow(InactiveAlarmException(key))

    val severityKeys   = metadataKeys.map(SeverityKey.fromMetadataKey)
    val severityValues = await(severityApi.mget(severityKeys))
    val severityList = severityValues.collect {
      case kv if kv.hasValue => kv.getValue
      case _                 => Disconnected
    }

    severityList.reduceRight((previous, current: AlarmSeverity) ⇒ previous max current)
  }

  final override def subscribeAggregatedSeverityCallback(key: Key, callback: AlarmSeverity ⇒ Unit): AlarmSubscription = {
    log.debug(s"Subscribe aggregated severity for alarm [${key.value}] with a callback")
    subscribeAggregatedSeverity(key)
      .to(Sink.foreach(callback))
      .run()
  }

  final override def subscribeAggregatedSeverityActorRef(key: Key, actorRef: ActorRef[AlarmSeverity]): AlarmSubscription = {
    log.debug(s"Subscribe aggregated severity for alarm [${key.value}] with an actor")
    subscribeAggregatedSeverityCallback(key, actorRef ! _)
  }

  final override def getCurrentSeverity(key: AlarmKey): Future[AlarmSeverity] = async {
    log.debug(s"Getting severity for alarm [${key.value}]")
    val metadataApi = await(metadataApiF)
    val severityApi = await(severityApiF)

    if (await(metadataApi.exists(key))) await(severityApi.get(key)).getOrElse(Disconnected)
    else logAndThrow(KeyNotFoundException(key))
  }

  private[alarm] def setCurrentSeverity(key: AlarmKey, severity: AlarmSeverity): Future[Unit] = async {
    log.debug(
      s"Setting severity [${severity.name}] for alarm [${key.value}] with expire timeout [${settings.ttlInSeconds}] seconds"
    )

    // get alarm metadata
    val alarm = await(getMetadata(key))

    // validate if the provided severity is supported by this alarm
    if (!alarm.allSupportedSeverities.contains(severity))
      logAndThrow(InvalidSeverityException(key, alarm.allSupportedSeverities, severity))

    // get the current severity of the alarm
    val severityApi = await(severityApiF)

    // set the severity of the alarm so that it does not transition to `Disconnected` state
    log.info(s"Updating current severity [${severity.name}] in alarm store")
    await(severityApi.setex(key, settings.ttlInSeconds, severity))
  }

  // PatternMessage gives three values:
  // pattern: e.g  __keyspace@0__:status.nfiraos.*.*,
  // channel: e.g. __keyspace@0__:status.nfiraos.trombone.tromboneAxisLowLimitAlarm,
  // message: event type as value: e.g. set, expire, expired
  private[alarm] def subscribeAggregatedSeverity(key: Key): Source[AlarmSeverity, AlarmSubscription] = {
    val redisStreamApi     = severityApiF.flatMap(severityApi ⇒ redisKeySpaceApi(severityApi)(AlarmCodec.SeverityCodec)) // create new connection for every client
    val keys: List[String] = List(SeverityKey.fromAlarmKey(key).value)

    def reducer(iterable: Iterable[Option[AlarmSeverity]]): AlarmSeverity =
      iterable.map(x => if (x.isEmpty) Disconnected else x.get).maxBy(_.level)

    Source
      .fromFutureSource(redisStreamApi.map(_.watchKeyspaceValueAggregation(keys, OverflowStrategy.LATEST, reducer)))
      .mapMaterializedValue { mat =>
        new AlarmSubscription {
          override def unsubscribe(): Future[Unit] = mat.flatMap(_.unsubscribe())
          override def ready(): Future[Unit]       = mat.flatMap(_.ready())
        }
      }
  }

  private def logAndThrow(runtimeException: RuntimeException) = {
    log.error(runtimeException.getMessage, ex = runtimeException)
    throw runtimeException
  }
}
