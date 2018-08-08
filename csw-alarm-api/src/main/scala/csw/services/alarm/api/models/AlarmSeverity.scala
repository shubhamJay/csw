package csw.services.alarm.api.models

import enumeratum.EnumEntry.Lowercase
import enumeratum.{Enum, EnumEntry}

import scala.collection.immutable.IndexedSeq

sealed abstract class AlarmSeverity private[alarm] (val level: Int, val latchable: Boolean) extends EnumEntry with Lowercase {

  /**
   * The name of SeverityLevels e.g. for Major severity level, the name will be represented as `major`
   */
  def name: String = entryName

  def >(otherSeverity: AlarmSeverity): Boolean = this.level > otherSeverity.level

  def max(otherSeverity: AlarmSeverity): AlarmSeverity = if (otherSeverity > this) otherSeverity else this

  // Disconnected, Indeterminate and Okay are not considered as an alarm condition
  def isHighRisk: Boolean = this.level > 0
}

object AlarmSeverity extends Enum[AlarmSeverity] {

  /**
   * Returns a sequence of all alarm severity
   */
  def values: IndexedSeq[AlarmSeverity] = findValues

  case object Okay          extends AlarmSeverity(0, true)
  case object Warning       extends AlarmSeverity(1, true)
  case object Major         extends AlarmSeverity(2, true)
  case object Indeterminate extends AlarmSeverity(3, true)
  case object Disconnected  extends AlarmSeverity(4, false)
  case object Critical      extends AlarmSeverity(5, true)
}
