package csw.services.alarm.client.internal

import akka.actor.ActorSystem
import csw.services.alarm.api.exceptions.InvalidSeverityException
import csw.services.alarm.api.models.AcknowledgementStatus.{Acknowledged, UnAcknowledged}
import csw.services.alarm.api.models.LatchStatus.Latched
import csw.services.alarm.api.models.{AlarmKey, AlarmMetadata, AlarmSeverity, AlarmStatus}
import csw.services.alarm.api.scaladsl.AlarmAdminService
import csw.services.alarm.client.internal.codec.{AlarmMetadataCodec, AlarmSeverityCodec, AlarmStatusCodec}
import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.{RedisClient, RedisURI}

import scala.async.Async._
import scala.compat.java8.FutureConverters.CompletionStageOps
import scala.concurrent.Future

class AlarmServiceImpl(redisURI: RedisURI, redisClient: RedisClient)(implicit actorSystem: ActorSystem)
    extends AlarmAdminService {

  import actorSystem.dispatcher

  private lazy val asyncMetadataApiF: Future[RedisAsyncCommands[AlarmKey, AlarmMetadata]] = Future.unit
    .flatMap(_ ⇒ redisClient.connectAsync(AlarmMetadataCodec, redisURI).toScala)
    .map(_.async())

  private lazy val asyncSeverityApiF: Future[RedisAsyncCommands[AlarmKey, AlarmSeverity]] = Future.unit
    .flatMap(_ ⇒ redisClient.connectAsync(AlarmSeverityCodec, redisURI).toScala)
    .map(_.async())

  private lazy val asyncStatusApiF: Future[RedisAsyncCommands[AlarmKey, AlarmStatus]] = Future.unit
    .flatMap(_ ⇒ redisClient.connectAsync(AlarmStatusCodec, redisURI).toScala)
    .map(_.async())

  private val refreshInSeconds       = actorSystem.settings.config.getInt("alarm.refresh-in-seconds") // default value is 5 seconds
  private val maxMissedRefreshCounts = actorSystem.settings.config.getInt("alarm.max-missed-refresh-counts") //default value is 3 times
  private val ttlInSeconds           = refreshInSeconds * maxMissedRefreshCounts

  override def setSeverity(key: AlarmKey, severity: AlarmSeverity): Future[Unit] = async {
    // get alarm metadata
    val metadataApi = await(asyncMetadataApiF)
    val alarm       = await(metadataApi.get(key).toScala)

    // validate if the provided severity is supported by this alarm
    if (!alarm.supportedSeverities.contains(severity))
      throw InvalidSeverityException(key, alarm.supportedSeverities, severity)

    // get the current severity of the alarm
    val severityApi     = await(asyncSeverityApiF)
    val currentSeverity = await(severityApi.get(key).toScala)

    // set the severity of the alarm so that it does not transition to `Disconnected` state
    await(severityApi.setex(key, ttlInSeconds, severity).toScala)

    // get alarm status
    val statusApi = await(asyncStatusApiF)
    var status    = await(statusApi.get(key).toScala)

    // derive latch status
    if (alarm.isLatchable && severity.isHighRisk && severity.isHigherThan(status.latchedSeverity))
      status = status.copy(latchStatus = Latched, latchedSeverity = severity)

    // derive acknowledgement status
    if (severity.isHighRisk && severity != currentSeverity) {
      if (alarm.isAutoAcknowledgable) status = status.copy(acknowledgementStatus = Acknowledged)
      else status = status.copy(acknowledgementStatus = UnAcknowledged)
    }

    // update alarm status
    await(statusApi.set(key, status).toScala)
  }

  override def getSeverity(key: AlarmKey): Future[AlarmSeverity] = async {
    val severityApi = await(asyncSeverityApiF)
    await(severityApi.get(key).toScala)
  }

  override def getMetadata(key: AlarmKey): Future[AlarmMetadata] = async {
    val metadataApi = await(asyncMetadataApiF)
    await(metadataApi.get(key).toScala)
  }

  override def getStatus(key: AlarmKey): Future[AlarmStatus] = async {
    val statusApi = await(asyncStatusApiF)
    await(statusApi.get(key).toScala)
  }
}
