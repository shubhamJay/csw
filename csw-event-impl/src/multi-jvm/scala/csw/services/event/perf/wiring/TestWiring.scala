package csw.services.event.perf.wiring

import akka.actor.ActorSystem
import csw.services.event.internal.wiring.{EventServiceResolver, Wiring}
import csw.services.event.scaladsl.{EventPublisher, EventSubscriber, KafkaFactory, RedisFactory}
import io.lettuce.core.RedisClient
import org.scalatest.mockito.MockitoSugar

class TestWiring(actorSystem: ActorSystem) extends MockitoSugar {

  lazy val testConfigs = new TestConfigs(actorSystem.settings.config)
  import testConfigs._
  val wiring = new Wiring(actorSystem)
  import wiring._

  lazy val redisFactory: RedisFactory = new RedisFactory(RedisClient.create(), mock[EventServiceResolver])
  lazy val kafkaFactory: KafkaFactory = new KafkaFactory(mock[EventServiceResolver])(actorSystem, ec, resumingMat)

  def publisher: EventPublisher =
    if (redisEnabled) redisFactory.publisher(redisHost, redisPort)
    else kafkaFactory.publisher(kafkaHost, kafkaPort)

  def subscriber: EventSubscriber =
    if (redisEnabled) redisFactory.subscriber(redisHost, redisPort)
    else kafkaFactory.subscriber(kafkaHost, kafkaPort)

}
