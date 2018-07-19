package csw.services.alarm.api.internal

import csw.services.alarm.api.models._
import upickle.default.readwriter
import upickle.default.{ReadWriter => RW, _}

trait AlarmRW {
  implicit val alarmKeyRW: RW[AlarmKey] = macroRW

  implicit val metadataKeyRW: RW[MetadataKey] = readwriter[String].bimap(_.key, MetadataKey.apply)
  implicit val statusKeyRW: RW[StatusKey]     = readwriter[String].bimap(_.key, StatusKey.apply)
  implicit val severityKeyRW: RW[SeverityKey] = readwriter[String].bimap(_.key, SeverityKey.apply)

  implicit val alarmMetadataRW: RW[AlarmMetadata] = macroRW
  implicit val alarmStatusRW: RW[AlarmStatus]     = macroRW
  implicit val alarmSeverityRW: RW[AlarmSeverity] = EnumUpickleSupport.enumFormat
}
