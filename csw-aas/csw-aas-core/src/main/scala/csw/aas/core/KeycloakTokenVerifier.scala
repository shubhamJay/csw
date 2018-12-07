package csw.aas.core
import org.keycloak.adapters.KeycloakDeployment
import org.keycloak.adapters.rotation.AdapterTokenVerifier
import org.keycloak.representations.{AccessToken => KeycloakAccessToken}

import scala.concurrent.{ExecutionContext, Future}

private[aas] class KeycloakTokenVerifier {
  def verifyToken(token: String,
                  keycloakDeployment: KeycloakDeployment)(implicit ec: ExecutionContext): Future[KeycloakAccessToken] = Future {
    scala.concurrent.blocking {
      AdapterTokenVerifier.verifyToken(token, keycloakDeployment)
    }
  }
}