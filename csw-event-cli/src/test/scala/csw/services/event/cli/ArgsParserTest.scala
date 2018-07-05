package csw.services.event.cli

import java.io.{ByteArrayOutputStream, File}

import csw.messages.events.EventKey
import org.scalatest.{FunSuite, Matchers}

import scala.concurrent.duration.DurationDouble

class ArgsParserTest extends FunSuite with Matchers {

  //Capture output/error generated by the parser, for cleaner test output. If interested, errCapture.toString will return capture errors.
  private val outCapture = new ByteArrayOutputStream
  private val errCapture = new ByteArrayOutputStream
  private val parser     = new ArgsParser("csw-event-cli")
  private val eventKeys  = Seq(EventKey("a.b.c"), EventKey("x.y.z"))

  def silentParse(args: Array[String]): Option[Options] =
    Console.withOut(outCapture) {
      Console.withErr(errCapture) {
        parser.parse(args)
      }
    }

  test("parse without arguments") {
    val args = Array("")
    silentParse(args) shouldBe None
  }

  // DEOPSCSW-364: [Event Cli] Inspect command
  test("parse inspect command without any options") {
    val args = Array("inspect")
    silentParse(args) shouldBe None
  }

  // DEOPSCSW-364: [Event Cli] Inspect command
  test("parse inspect command with only mandatory options") {
    val args = Array("inspect", "-e", "a.b.c,x.y.z")
    silentParse(args) shouldBe Some(Options("inspect", eventKeys = eventKeys))
  }

  test("parse get command without any options") {
    val args = Array("get")
    silentParse(args) shouldBe None
  }

  test("parse get with only mandatory options") {
    val args = Array("get", "-e", "a.b.c,x.y.z")
    silentParse(args) shouldBe Some(Options("get", eventsMap = Map(EventKey("a.b.c") → Set(), EventKey("x.y.z") → Set())))
  }

  test("parse get with json output") {
    val args = Array("get", "-e", "a.b.c,x.y.z:k2:k3", "-o", "json")
    silentParse(args) shouldBe Some(
      Options("get", eventsMap = Map(EventKey("a.b.c") → Set(), EventKey("x.y.z") → Set("k2", "k3")), out = "json")
    )
  }

  test("parse get with all options") {
    val args = Array("get", "-e", "a.b.c,x.y.z:k2:k3", "-o", "oneline", "-t", "-i", "-u")
    silentParse(args) shouldBe Some(
      Options(
        "get",
        eventsMap = Map(EventKey("a.b.c") → Set(), EventKey("x.y.z") → Set("k2", "k3")),
        printTimestamp = true,
        printId = true,
        printUnits = true
      )
    )
  }

  // DEOPSCSW-432: [Event Cli] Publish command
  test("parse publish when input data file does not exist") {
    val args = Array("publish", "-e", "a.b.c", "--data", "./observe_event.json")
    silentParse(args) shouldBe None
  }

  // DEOPSCSW-432: [Event Cli] Publish command
  test("parse publish with mandatory fields when input data file exist") {
    val observeEventJson = File.createTempFile("observe_event", "json")
    val args             = Array("publish", "--data", observeEventJson.getAbsolutePath)
    observeEventJson.deleteOnExit()

    silentParse(args) shouldBe Some(
      Options("publish", eventData = observeEventJson)
    )
  }

  // DEOPSCSW-432: [Event Cli] Publish command
  test("parse publish with all fields when input data file exist") {
    val observeEventJson = File.createTempFile("observe_event", "json")
    val args             = Array("publish", "-e", "a.b.c", "--data", observeEventJson.getAbsolutePath, "-i", "20", "--period", "10")
    observeEventJson.deleteOnExit()

    silentParse(args) shouldBe Some(
      Options(
        "publish",
        eventKey = Some(EventKey("a.b.c")),
        eventData = observeEventJson,
        interval = Some(20.millis),
        period = 10.seconds
      )
    )
  }
}
