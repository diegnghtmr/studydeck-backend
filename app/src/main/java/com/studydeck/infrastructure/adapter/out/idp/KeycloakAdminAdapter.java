package com.studydeck.infrastructure.adapter.out.idp;

import com.studydeck.domain.port.out.IdpAdminPort;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Output adapter implementing {@link IdpAdminPort} via the Keycloak Admin REST API.
 *
 * <p>Authenticates against Keycloak's master realm using a password grant on the {@code admin-cli}
 * client, then calls realm-scoped Admin REST endpoints.
 *
 * <p>NEVER logs credentials. Throws {@link IdpAdminException} on non-2xx responses.
 */
class KeycloakAdminAdapter implements IdpAdminPort {

  private static final Logger log = LoggerFactory.getLogger(KeycloakAdminAdapter.class);

  private final RestClient restClient;
  private final String baseUrl;
  private final String realm;
  private final String adminUsername;
  private final String adminPassword;
  private final String adminClientId;

  KeycloakAdminAdapter(
      RestClient restClient,
      String baseUrl,
      String realm,
      String adminUsername,
      String adminPassword,
      String adminClientId) {
    this.restClient = restClient;
    this.baseUrl = baseUrl;
    this.realm = realm;
    this.adminUsername = adminUsername;
    this.adminPassword = adminPassword;
    this.adminClientId = adminClientId;
  }

  @Override
  public void deleteUser(String idpUserId) {
    String token = acquireAdminToken();
    String url = baseUrl + "/admin/realms/" + realm + "/users/" + idpUserId;
    try {
      restClient
          .delete()
          .uri(url)
          .header("Authorization", "Bearer " + token)
          .retrieve()
          .toBodilessEntity();
      log.debug("Deleted Keycloak user {}", idpUserId);
    } catch (RestClientException e) {
      throw new IdpAdminException("Failed to delete Keycloak user " + idpUserId, e);
    }
  }

  @Override
  public void logoutAllSessions(String idpUserId) {
    String token = acquireAdminToken();
    String url = baseUrl + "/admin/realms/" + realm + "/users/" + idpUserId + "/logout";
    try {
      restClient
          .post()
          .uri(url)
          .header("Authorization", "Bearer " + token)
          .retrieve()
          .toBodilessEntity();
      log.debug("Logged out all sessions for Keycloak user {}", idpUserId);
    } catch (RestClientException e) {
      throw new IdpAdminException("Failed to logout Keycloak user " + idpUserId, e);
    }
  }

  private String acquireAdminToken() {
    String tokenUrl = baseUrl + "/realms/master/protocol/openid-connect/token";
    String body =
        "grant_type=password"
            + "&client_id="
            + adminClientId
            + "&username="
            + adminUsername
            + "&password="
            + adminPassword;
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> response =
          restClient
              .post()
              .uri(tokenUrl)
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(body)
              .retrieve()
              .body(Map.class);
      if (response == null || !response.containsKey("access_token")) {
        throw new IdpAdminException("Keycloak token response missing access_token");
      }
      return (String) response.get("access_token");
    } catch (RestClientException e) {
      throw new IdpAdminException("Failed to acquire Keycloak admin token", e);
    }
  }
}
