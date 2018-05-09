package csw.services.event.perf

import java.io.PrintStream
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.{ExecutorService, Executors}

import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec, MultiNodeSpecCallbacks}
import akka.testkit.{ImplicitSender, TestProbe}
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import csw.messages.events.{Event, SystemEvent}
import csw.messages.params.models.Subsystem.{AOESW, IRIS, NFIRAOS, TCS, WFOS}
import csw.services.event.internal.wiring.Wiring
import csw.services.event.perf.EventUtils.{nanosToMicros, nanosToSeconds}
import org.HdrHistogram.Histogram
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration.{Duration, DurationLong}

sealed trait BaseSetting {
  def subsystem: String
  def totalTestMsgs: Long
  def rate: Int
  def payloadSize: Int

  val warmup: Int = rate * 20
  val key         = s"$subsystem-${rate}Hz"
}

case class PubSetting(subsystem: String, noOfPubs: Int, totalTestMsgs: Long, rate: Int, payloadSize: Int) extends BaseSetting
case class SubSetting(subsystem: String, noOfSubs: Int, totalTestMsgs: Long, rate: Int, payloadSize: Int) extends BaseSetting

case class JvmSetting(name: String, pubSettings: List[PubSetting], subSettings: List[SubSetting])

case class ModelObservatoryTestSettings(jvmSettings: List[JvmSetting])

object ModelObservatoryTest extends MultiNodeConfig {

  val totalNumberOfNodes: Int =
    System.getProperty("csw.event.ModelObservatoryTest.nrOfNodes") match {
      case null  ⇒ 21
      case value ⇒ value.toInt
    }

  for (n ← 0 until totalNumberOfNodes) role("node-" + n)

  commonConfig(debugConfig(on = false).withFallback(ConfigFactory.load()))

}

class ModelObservatoryTestMultiJvmNode1  extends ModelObservatoryTest
class ModelObservatoryTestMultiJvmNode2  extends ModelObservatoryTest
class ModelObservatoryTestMultiJvmNode3  extends ModelObservatoryTest
class ModelObservatoryTestMultiJvmNode4  extends ModelObservatoryTest
class ModelObservatoryTestMultiJvmNode5  extends ModelObservatoryTest
class ModelObservatoryTestMultiJvmNode6  extends ModelObservatoryTest
class ModelObservatoryTestMultiJvmNode7  extends ModelObservatoryTest
class ModelObservatoryTestMultiJvmNode8  extends ModelObservatoryTest
class ModelObservatoryTestMultiJvmNode9  extends ModelObservatoryTest
class ModelObservatoryTestMultiJvmNode10 extends ModelObservatoryTest
class ModelObservatoryTestMultiJvmNode11 extends ModelObservatoryTest
class ModelObservatoryTestMultiJvmNode12 extends ModelObservatoryTest
class ModelObservatoryTestMultiJvmNode13 extends ModelObservatoryTest
class ModelObservatoryTestMultiJvmNode14 extends ModelObservatoryTest
class ModelObservatoryTestMultiJvmNode15 extends ModelObservatoryTest
class ModelObservatoryTestMultiJvmNode16 extends ModelObservatoryTest
class ModelObservatoryTestMultiJvmNode17 extends ModelObservatoryTest
class ModelObservatoryTestMultiJvmNode18 extends ModelObservatoryTest
class ModelObservatoryTestMultiJvmNode19 extends ModelObservatoryTest
class ModelObservatoryTestMultiJvmNode20 extends ModelObservatoryTest
class ModelObservatoryTestMultiJvmNode21 extends ModelObservatoryTest

class ModelObservatoryTest
    extends MultiNodeSpec(ModelObservatoryTest)
    with MultiNodeSpecCallbacks
    with FunSuiteLike
    with Matchers
    with ImplicitSender
    with PerfFlamesSupport
    with BeforeAndAfterAll {

  private val testConfigs = new TestConfigs(system.settings.config)
  import testConfigs._
  private val testWiring = new TestWiring(system, new Wiring(system))

  def adjustedTotalMessages(n: Long): Long = (n * totalMessagesFactor).toLong

  override def initialParticipants: Int = roles.size

  lazy val reporterExecutor: ExecutorService = Executors.newFixedThreadPool(1)
  def reporter(name: String): TestRateReporter = {
    val r = new TestRateReporter(name)
    reporterExecutor.execute(r)
    r
  }

  var throughputPlots: PlotResult = PlotResult()
  var latencyPlots: LatencyPlots  = LatencyPlots()

  override def beforeAll(): Unit = multiNodeSpecBeforeAll()

  override def afterAll(): Unit = {
    reporterExecutor.shutdown()
    runOn(roles.last) {
      println("================================ Throughput msgs/s ================================")
      throughputPlots.printTable()
      println()
      latencyPlots.printTable(system.name)
    }
    multiNodeSpecAfterAll()
  }

  val settings: List[JvmSetting] =
  JvmSetting(
    TCS.entryName,
    List(
      PubSetting(s"${TCS.entryName}-1", noOfPubs = 3, adjustedTotalMessages(6000), rate = 100, payloadSize = 128),
      PubSetting(s"${TCS.entryName}-1", noOfPubs = 25, adjustedTotalMessages(1200), rate = 20, payloadSize = 128),
      PubSetting(s"${TCS.entryName}-1", noOfPubs = 250, adjustedTotalMessages(60), rate = 1, payloadSize = 128)
    ),
    List(
      SubSetting(s"${TCS.entryName}-1", noOfSubs = 3, adjustedTotalMessages(6000), rate = 100, payloadSize = 128),
      SubSetting(s"${TCS.entryName}-1", noOfSubs = 25, adjustedTotalMessages(1200), rate = 20, payloadSize = 128),
      SubSetting(s"${TCS.entryName}-1", noOfSubs = 250, adjustedTotalMessages(60), rate = 1, payloadSize = 128)
    )
  ) ::
  List(AOESW, IRIS, NFIRAOS, WFOS).flatMap { subsystem ⇒
    val subsystemName = subsystem.entryName

    (0 to 5).map { n ⇒
      JvmSetting(
        subsystemName,
        List(
          PubSetting(s"$subsystemName-$n", noOfPubs = 5, adjustedTotalMessages(1200), rate = 20, payloadSize = 128),
          PubSetting(s"$subsystemName-$n", noOfPubs = 50, adjustedTotalMessages(60), rate = 1, payloadSize = 128)
        ),
        subsystem match {
          case AOESW ⇒
            List(
              SubSetting(s"${IRIS.entryName}-$n", noOfSubs = 5, adjustedTotalMessages(1200), rate = 20, payloadSize = 128),
              SubSetting(s"${IRIS.entryName}-$n", noOfSubs = 50, adjustedTotalMessages(60), rate = 1, payloadSize = 128)
            )
          case IRIS ⇒
            List(
//              SubSetting(s"${TCS.entryName}-1", noOfSubs = 1, adjustedTotalMessages(6000), rate = 100, payloadSize = 128),
              SubSetting(s"${AOESW.entryName}-$n", noOfSubs = 5, adjustedTotalMessages(1200), rate = 20, payloadSize = 128),
              SubSetting(s"${AOESW.entryName}-$n", noOfSubs = 50, adjustedTotalMessages(60), rate = 1, payloadSize = 128)
            )
          case NFIRAOS ⇒
            List(
//              SubSetting(s"${TCS.entryName}-1", noOfSubs = 1, adjustedTotalMessages(6000), rate = 100, payloadSize = 128),
              SubSetting(s"${WFOS.entryName}-$n", noOfSubs = 5, adjustedTotalMessages(1200), rate = 20, payloadSize = 128),
              SubSetting(s"${WFOS.entryName}-$n", noOfSubs = 50, adjustedTotalMessages(60), rate = 1, payloadSize = 128)
            )
          case WFOS ⇒
            List(
//              SubSetting(s"${TCS.entryName}-1", noOfSubs = 3, adjustedTotalMessages(6000), rate = 100, payloadSize = 128),
              SubSetting(s"${NFIRAOS.entryName}-$n", noOfSubs = 5, adjustedTotalMessages(1200), rate = 20, payloadSize = 128),
              SubSetting(s"${NFIRAOS.entryName}-$n", noOfSubs = 50, adjustedTotalMessages(60), rate = 1, payloadSize = 128)
            )
        }
      )
    }
  }

  val testSettings = ModelObservatoryTestSettings(settings)

  test("model observatory test") {
    val nodeId = myself.name.split("-").tail.head.toInt

    runOn(roles: _*) {
      val jvmSetting = testSettings.jvmSettings(nodeId)
      import jvmSetting._

      val rep = reporter(s"ModelObsTest-$nodeId")

      val subscribers = subSettings.flatMap { subSetting ⇒
        import subSetting._
        (1 to noOfSubs).map { subId ⇒
          val subscriber =
            new ModelObsSubscriber(s"${subSetting.key}-$subId", subSetting, rep, testWiring)
          val doneF = subscriber.startSubscription()
          (doneF, subscriber)
        }
      }

      val completionProbe = TestProbe()

      var totalTimePerNode            = 0L
      var eventsReceivedPerNode       = 0L
      val histogramPerNode: Histogram = new Histogram(SECONDS.toNanos(10), 3)

      var aggregatedThroughput: Double   = 0
      var aggregatedHistogram: Histogram = null

      runOn(roles.last) {
        aggregatedHistogram = new Histogram(SECONDS.toNanos(10), 3)
        var newEvent               = false
        var receivedPerfEventCount = 0

        def onEvent(event: Event): Unit = event match {
          case event: SystemEvent if newEvent ⇒
            receivedPerfEventCount += 1
            val histogramBuffer = ByteString(event.get(EventUtils.histogramKey).get.values).asByteBuffer
            aggregatedHistogram.add(Histogram.decodeFromByteBuffer(histogramBuffer, SECONDS.toNanos(10)))

            aggregatedThroughput += event.get(EventUtils.throughputKey).get.head

            if (receivedPerfEventCount == roles.size) completionProbe.ref ! "completed"
          case _ ⇒ newEvent = true
        }

        val subscriber        = testWiring.subscriber
        val eventSubscription = subscriber.subscribeCallback(Set(EventUtils.perfEventKey), onEvent)
        Await.result(eventSubscription.ready(), 30.seconds)
      }

      enterBarrier("subscribers-started")

      pubSettings.foreach { pubSetting ⇒
        import pubSetting._
        (1 to noOfPubs).foreach { pubId ⇒
          new ModelObsPublisher(s"${pubSetting.key}-$pubId", pubSetting, testWiring)
            .startPublishingWithEventGenerator()
        }
      }

      enterBarrier("publishers-started")

      subscribers.foreach {
        case (doneF, subscriber) ⇒
          Await.result(doneF, Duration.Inf)
          subscriber.printResult()

          histogramPerNode.add(subscriber.histogram)
          eventsReceivedPerNode += subscriber.eventsReceived
          totalTimePerNode = Math.max(totalTimePerNode, subscriber.totalTime)
      }

      val byteBuffer: ByteBuffer = ByteBuffer.allocate(326942)
      histogramPerNode.encodeIntoByteBuffer(byteBuffer)

      val publisher = testWiring.publisher
      Await.result(
        publisher.publish(
          EventUtils.perfResultEvent(byteBuffer.array(), eventsReceivedPerNode / nanosToSeconds(totalTimePerNode))
        ),
        30.seconds
      )

      runOn(roles.last) {
        completionProbe.receiveOne(30.seconds)
        aggregateResult("ModelObsTest", aggregatedThroughput, aggregatedHistogram)
      }

      enterBarrier("done")
      rep.halt()
    }
  }

  private def aggregateResult(testName: String, throughput: Double, aggregatedHistogram: Histogram): Unit = {
    throughputPlots = throughputPlots.addAll(PlotResult().add(testName, throughput))

    def percentile(p: Double): Double = nanosToMicros(aggregatedHistogram.getValueAtPercentile(p))

    val latencyPlotsTmp = LatencyPlots(
      PlotResult().add(testName, percentile(50.0)),
      PlotResult().add(testName, percentile(90.0)),
      PlotResult().add(testName, percentile(99.0))
    )

    latencyPlots = latencyPlots.copy(
      plot50 = latencyPlots.plot50.addAll(latencyPlotsTmp.plot50),
      plot90 = latencyPlots.plot90.addAll(latencyPlotsTmp.plot90),
      plot99 = latencyPlots.plot99.addAll(latencyPlotsTmp.plot99)
    )

    aggregatedHistogram.outputPercentileDistribution(
      new PrintStream(BenchmarkFileReporter(s"Aggregated-$testName", system, logSettings = false).fos),
      1000.0
    )
  }

}