package com.studydeck.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Keycloak Admin REST API access.
 *
 * <p>All values default to empty strings (from env vars with empty defaults) so the app boots
 * safely without Keycloak admin credentials configured.
 */
@ConfigurationProperties(prefix = "studydeck.idp.admin")
public record IdpAdminProperties(
    /** Base URL of the Keycloak server (e.g., http://keycloak:8080). Empty = admin disabled. */
    String baseUrl,
    /** Keycloak realm for the application (e.g., studydeck). */
    String realm,
    /** Keycloak admin username (master realm). */
    String username,
    /** Keycloak admin password (master realm). */
    String password,
    /** Keycloak admin client ID (default: admin-cli). */
    String clientId) {

  public IdpAdminProperties {
    baseUrl = baseUrl != null ? baseUrl : "";
    realm = realm != null ? realm : "";
    username = username != null ? username : "";
    password = password != null ? password : "";
    clientId = clientId != null ? clientId : "admin-cli";
  }

  /** Returns true when the base URL is configured (non-blank). */
  public boolean isEnabled() {
    return baseUrl != null && !baseUrl.isBlank();
  }
}
