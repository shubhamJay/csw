package csw.services.alarm.client.internal

import java.util.concurrent.CompletableFuture

import csw.services.alarm.api.javadsl.IAlarmService
import csw.services.alarm.api.models.{ExplicitAlarmSeverity, Key}
import csw.services.alarm.api.scaladsl.AlarmService

import scala.compat.java8.FutureConverters.FutureOps

class JAlarmServiceImpl(alarmService: AlarmService) extends IAlarmService {
  override def setSeverity(key: Key.AlarmKey, severity: ExplicitAlarmSeverity): CompletableFuture[Unit] =
    alarmService.setSeverity(key, severity).toJava.toCompletableFuture
  override def asScala: AlarmService = alarmService
}
