package csw.services.alarm.api.internal
import java.util.regex.Pattern

object RichStringExtentions {
  implicit class RichString(val value: String) extends AnyVal {
    def matches(pattern: Pattern): Boolean = pattern.matcher(value).matches()
  }
}