package csw.services.event.cli

import org.scalatest.{BeforeAndAfterEach, FunSuite, Matchers}
import ujson.Js
import upickle.default.read

import scala.collection.mutable
import scala.io.Source

class EventJsonTransformerTest extends FunSuite with Matchers with BeforeAndAfterEach {

  private var logBuffer = mutable.Buffer.empty[String]

  override def beforeEach(): Unit = {
    logBuffer = mutable.Buffer.empty
  }

  test("should not printLine if json is true") {

    val eventJson = read[Js.Obj](Source.fromResource("get/input/event1.json").mkString)
    EventJsonTransformer.transformInPlace(eventJson, List("epoch"))
    logBuffer shouldBe empty
  }

  test("should be able to get entire event json when no paths are specified") {

    val jsonString        = Source.fromResource("get/input/event1.json").mkString
    val eventJson         = read[Js.Obj](jsonString)
    val expectedEventJson = read[Js.Obj](jsonString)

    EventJsonTransformer.transformInPlace(eventJson, Nil)
    eventJson shouldBe expectedEventJson
  }

  test("should be able to get top level key non struct key from json") {

    val eventJson         = read[Js.Obj](Source.fromResource("get/input/event1.json").mkString)
    val expectedEventJson = read[Js.Obj](Source.fromResource("get/expected/top_level_non_struct_key.json").mkString)
    val paths             = List("epoch")
    EventJsonTransformer.transformInPlace(eventJson, paths)
    eventJson shouldBe expectedEventJson
  }

  test("should be able to get top level partial struct key from json") {

    val eventJson         = read[Js.Obj](Source.fromResource("get/input/event1.json").mkString)
    val expectedEventJson = read[Js.Obj](Source.fromResource("get/expected/top_level_partial_struct_key.json").mkString)
    val paths             = List("struct-1")
    EventJsonTransformer.transformInPlace(eventJson, paths)
    eventJson shouldBe expectedEventJson
  }

  test("should be able to get specified paths two levels deep in event in json format") {

    val eventJson         = read[Js.Obj](Source.fromResource("get/input/event1.json").mkString)
    val expectedEventJson = read[Js.Obj](Source.fromResource("get/expected/get_path_2_levels_deep.json").mkString)
    val paths             = List("struct-1/ra")
    EventJsonTransformer.transformInPlace(eventJson, paths)
    expectedEventJson shouldBe eventJson
  }

  test("should be able to get multiple specified paths in event in json format") {

    val eventJson         = read[Js.Obj](Source.fromResource("get/input/event1.json").mkString)
    val expectedEventJson = read[Js.Obj](Source.fromResource("get/expected/get_multiple_paths.json").mkString)
    val paths             = List("struct-1/ra", "epoch")
    EventJsonTransformer.transformInPlace(eventJson, paths)
    expectedEventJson shouldBe eventJson
  }

  test("should be able to get specified paths for multiple events in json format") {

    val event1Json         = read[Js.Obj](Source.fromResource("get/input/event1.json").mkString)
    val event2Json         = read[Js.Obj](Source.fromResource("get/input/event2.json").mkString)
    val expectedEvent1Json = read[Js.Obj](Source.fromResource("get/expected/get_multiple_events1.json").mkString)
    val expectedEvent2Json = read[Js.Obj](Source.fromResource("get/expected/get_multiple_events2.json").mkString)

    EventJsonTransformer.transformInPlace(event1Json, List("struct-1/ra"))
    EventJsonTransformer.transformInPlace(event2Json, List("struct-2/struct-1/ra"))
    event1Json shouldBe expectedEvent1Json
    event2Json shouldBe expectedEvent2Json
  }
}
