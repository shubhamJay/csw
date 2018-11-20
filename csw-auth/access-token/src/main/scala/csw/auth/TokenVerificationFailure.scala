package csw.auth

sealed trait TokenVerificationFailure extends TokenError

object TokenVerificationFailure {
  case object TokenExpired                                              extends TokenVerificationFailure
  final case class InvalidToken(error: String = "Invalid Token Format") extends TokenVerificationFailure
  case object KidMissing                                                extends TokenVerificationFailure
  case object TokenMissing                                              extends TokenVerificationFailure
}