package com.studydeck.infrastructure.adapter.out.idp;

import com.studydeck.domain.port.out.IdpAdminPort;
import org.springframework.web.client.RestClient;

/** Factory for creating IdpAdminPort adapter instances. */
public final class IdpAdminAdapters {

  private IdpAdminAdapters() {}

  public static IdpAdminPort keycloak(
      RestClient restClient,
      String baseUrl,
      String realm,
      String adminUsername,
      String adminPassword,
      String adminClientId) {
    return new KeycloakAdminAdapter(
        restClient, baseUrl, realm, adminUsername, adminPassword, adminClientId);
  }

  public static IdpAdminPort noOp() {
    return new NoOpIdpAdminAdapter();
  }
}
