package csw.contract.generator

import io.bullet.borer.Codec
import io.bullet.borer.derivation.CompactMapBasedCodecs.deriveCodec

object ContractCodecs extends ContractCodecs
trait ContractCodecs {
  implicit lazy val endpointCodec: Codec[Endpoint] = deriveCodec
  implicit lazy val modelCodec: Codec[ModelType]   = deriveCodec

  implicit lazy val serviceCodec: Codec[Service]   = deriveCodec
  implicit lazy val servicesCodec: Codec[Services] = deriveCodec
  implicit lazy val contractCodec: Codec[Contract] = deriveCodec
}