package csw.services.event.perf

import java.time.Instant
import java.util.concurrent.TimeUnit.SECONDS

import akka.Done
import akka.stream.scaladsl.{Keep, Source}
import csw.messages.events.{Event, EventKey, EventName, SystemEvent}
import csw.services.event.perf.EventUtils._
import csw.services.event.scaladsl.{EventSubscriber, EventSubscription}
import org.HdrHistogram.Histogram

import scala.concurrent.Future

class ModelObsSubscriber(subscribeKey: String, subSetting: SubSetting, reporter: TestRateReporter, testWiring: TestWiring) {

  import testWiring.wiring._
  import subSetting._

  private val subscriber: EventSubscriber = testWiring.subscriber

  val histogram: Histogram = new Histogram(SECONDS.toNanos(10), 3)

  private val resultReporter = new ResultReporter(subsystem, actorSystem)
  val subId: Int             = subscribeKey.split("-").last.toInt

  var startTime       = 0L
  var totalTime       = 0L
  var eventsReceived  = 0L
  var lastId          = 0
  var outOfOrderCount = 0
  var lastCurrentId   = 0

  private val eventKeys    = Set(EventKey(s"$testEventKey-$subscribeKey"), EventKey(s"${prefix.prefix}.$endEventS-$subscribeKey"))
  private val eventsToDrop = warmup + eventKeys.size //inclusive of latest events from subscription

  val subscription: Source[Event, EventSubscription] = subscriber.subscribe(eventKeys)
  val endEventName                                   = EventName(s"${EventUtils.endEventS}-$subscribeKey")

  def startSubscription(): Future[Done] =
    subscription
      .drop(eventsToDrop)
      .takeWhile {
        case SystemEvent(_, _, `endEventName`, _, _) ⇒ false
        case _                                       ⇒ true
      }
      .watchTermination()(Keep.right)
      .runForeach(report)

  private def report(event: Event): Unit = {

    if (eventsReceived == 0)
      startTime = getNanos(Instant.now()).toLong

    eventsReceived += 1
    val currentTime = getNanos(Instant.now()).toLong
    totalTime = currentTime - startTime

    reporter.onMessage(1, payloadSize)

    val latency = (getNanos(Instant.now()) - getNanos(event.eventTime.time)).toLong
    try {
      histogram.recordValue(latency)
    } catch {
      case _: ArrayIndexOutOfBoundsException ⇒
    }

    val currentId = event.eventId.id.toInt
    val inOrder   = currentId >= lastId
    lastId = currentId

    if (!inOrder) outOfOrderCount += 1
  }

  def printResult(): Unit =
    resultReporter.printModelObsResult(subSetting, subId, histogram, eventsReceived, totalTime, outOfOrderCount)

}