package csw.apps.deployment.containercmd.cli

import java.io.ByteArrayOutputStream
import java.nio.file.Paths

import org.scalatest.{BeforeAndAfterEach, Matchers}

class ArgsParserTest extends org.scalatest.FunSuite with Matchers with BeforeAndAfterEach {
  val inputFilePath = "/tmp/some/input/file"

  //Capture output/error generated by the parser, for cleaner test output. If interested, errCapture.toString will return capture errors.
  val outCapture = new ByteArrayOutputStream
  val errCapture = new ByteArrayOutputStream

  override protected def afterEach(): Unit = {
    outCapture.reset()
    errCapture.reset()
  }

  def silentParse(args: Array[String]): Option[Options] =
    Console.withOut(outCapture) {
      Console.withErr(errCapture) {
        new ArgsParser().parser.parse(args, Options())
      }
    }

  test("should parse arguments when all arguments are provided") {
    val args                     = Array("--standalone", "--local", inputFilePath)
    val options: Option[Options] = silentParse(args)
    options should contain(Options(standalone = true, local = true, Some(Paths.get(inputFilePath))))
  }

  test("should parse arguments with default value of false when standalone option is not provided") {
    val args                     = Array("--local", inputFilePath)
    val options: Option[Options] = silentParse(args)
    options should contain(Options(standalone = false, local = true, Some(Paths.get(inputFilePath))))
  }

  test("should parse arguments with default value of false when local option is not provided") {
    val args                     = Array("--standalone", inputFilePath)
    val options: Option[Options] = silentParse(args)
    options should contain(Options(standalone = true, local = false, Some(Paths.get(inputFilePath))))
  }
}
