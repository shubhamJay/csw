package csw.location.model.codecs

import java.net.URI

import csw.location.model.scaladsl._
import csw.params.core.formats.{CborHelpers, CommonCodecs}
import io.bullet.borer.Codec
import io.bullet.borer.derivation.MapBasedCodecs._

object LocationCodecs extends LocationCodecs
trait LocationCodecs extends CommonCodecs {
  implicit lazy val connectionTypeCodec: Codec[ConnectionType] = CborHelpers.enumCodec[ConnectionType]
  implicit lazy val componentTypeCodec: Codec[ComponentType]   = CborHelpers.enumCodec[ComponentType]
  implicit lazy val componentIdCodec: Codec[ComponentId]       = deriveCodec[ComponentId]
  implicit lazy val connectionInfoCodec: Codec[ConnectionInfo] = deriveCodec[ConnectionInfo]

  implicit def connectionCodec[C <: Connection]: Codec[C] =
    CborHelpers.bimap[ConnectionInfo, C](x => Connection.from(x).asInstanceOf[C], _.connectionInfo)

  implicit lazy val uriCodec: Codec[URI] = CborHelpers.bimap[String, URI](new URI(_), _.toString)

  implicit lazy val locationCodec: Codec[Location]         = deriveCodec[Location]
  implicit lazy val akkaLocationCodec: Codec[AkkaLocation] = deriveCodec[AkkaLocation]
  implicit lazy val httpLocationCodec: Codec[HttpLocation] = deriveCodec[HttpLocation]

  implicit lazy val registrationCodec: Codec[Registration]         = deriveCodec[Registration]
  implicit lazy val akkaRegistrationCodec: Codec[AkkaRegistration] = deriveCodec[AkkaRegistration]
  implicit lazy val tcpRegistrationCodec: Codec[TcpRegistration]   = deriveCodec[TcpRegistration]
  implicit lazy val httpRegistrationCodec: Codec[HttpRegistration] = deriveCodec[HttpRegistration]

  implicit lazy val trackingEventCodec: Codec[TrackingEvent]     = deriveCodec[TrackingEvent]
  implicit lazy val locationUpdatedCodec: Codec[LocationUpdated] = deriveCodec[LocationUpdated]
  implicit lazy val locationRemovedCodec: Codec[LocationRemoved] = deriveCodec[LocationRemoved]
}