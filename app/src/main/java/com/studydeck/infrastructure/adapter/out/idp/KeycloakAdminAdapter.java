package com.studydeck.infrastructure.adapter.out.idp;

import com.studydeck.domain.model.IdpSession;
import com.studydeck.domain.port.out.IdpAdminPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
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

  @Override
  public List<IdpSession> listSessions(String idpUserId) {
    String token = acquireAdminToken();
    String url = baseUrl + "/admin/realms/" + realm + "/users/" + idpUserId + "/sessions";
    try {
      List<Map<String, Object>> response =
          restClient
              .get()
              .uri(url)
              .header("Authorization", "Bearer " + token)
              .retrieve()
              .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
      if (response == null) {
        return List.of();
      }
      List<IdpSession> sessions = new ArrayList<>();
      for (Map<String, Object> entry : response) {
        sessions.add(mapToIdpSession(entry));
      }
      return List.copyOf(sessions);
    } catch (RestClientException e) {
      throw new IdpAdminException("Failed to list sessions for Keycloak user " + idpUserId, e);
    }
  }

  @Override
  public void revokeSession(String sessionId) {
    String token = acquireAdminToken();
    String url = baseUrl + "/admin/realms/" + realm + "/sessions/" + sessionId;
    try {
      restClient
          .delete()
          .uri(url)
          .header("Authorization", "Bearer " + token)
          .retrieve()
          .toBodilessEntity();
      log.debug("Revoked Keycloak session {}", sessionId);
    } catch (RestClientException e) {
      throw new IdpAdminException("Failed to revoke Keycloak session " + sessionId, e);
    }
  }

  private IdpSession mapToIdpSession(Map<String, Object> entry) {
    String id = entry.containsKey("id") ? String.valueOf(entry.get("id")) : "unknown";
    String ipAddress =
        entry.containsKey("ipAddress") ? String.valueOf(entry.get("ipAddress")) : "unknown";

    Instant started = Instant.EPOCH;
    if (entry.containsKey("start")) {
      Object start = entry.get("start");
      if (start instanceof Number n) {
        started = Instant.ofEpochMilli(n.longValue());
      }
    }

    Instant lastAccess = Instant.EPOCH;
    if (entry.containsKey("lastAccess")) {
      Object la = entry.get("lastAccess");
      if (la instanceof Number n) {
        lastAccess = Instant.ofEpochMilli(n.longValue());
      }
    }

    List<String> clients = List.of();
    if (entry.containsKey("clients")) {
      Object clientsObj = entry.get("clients");
      if (clientsObj instanceof Map<?, ?> clientsMap) {
        clients = clientsMap.values().stream().map(Object::toString).toList();
      }
    }

    return new IdpSession(id, ipAddress, started, lastAccess, clients);
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
