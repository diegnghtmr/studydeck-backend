package com.studydeck.infrastructure.config;

import com.studydeck.application.service.ListSessionsService;
import com.studydeck.application.service.LogoutAllSessionsService;
import com.studydeck.application.service.RevokeSessionService;
import com.studydeck.domain.port.in.ListSessionsQuery;
import com.studydeck.domain.port.in.LogoutAllSessionsUseCase;
import com.studydeck.domain.port.in.RevokeSessionUseCase;
import com.studydeck.domain.port.out.AuditEventPort;
import com.studydeck.domain.port.out.IdpAdminPort;
import com.studydeck.infrastructure.adapter.out.idp.IdpAdminAdapters;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Wires the IdP admin output port and the session use cases.
 *
 * <p>Bean selection:
 *
 * <ul>
 *   <li>When {@code studydeck.idp.admin.base-url} is set (non-blank) → {@link
 *       com.studydeck.infrastructure.adapter.out.idp.KeycloakAdminAdapter}
 *   <li>Otherwise → {@link com.studydeck.infrastructure.adapter.out.idp.NoOpIdpAdminAdapter} (safe
 *       default; app boots without Keycloak admin)
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(IdpAdminProperties.class)
public class IdpAdminConfiguration {

  @Bean
  IdpAdminPort idpAdminPort(IdpAdminProperties props) {
    if (props.isEnabled()) {
      return IdpAdminAdapters.keycloak(
          RestClient.create(),
          props.baseUrl(),
          props.realm(),
          props.username(),
          props.password(),
          props.clientId());
    }
    return IdpAdminAdapters.noOp();
  }

  @Bean
  LogoutAllSessionsUseCase logoutAllSessionsUseCase(IdpAdminPort idpAdminPort) {
    return new LogoutAllSessionsService(idpAdminPort);
  }

  @Bean
  ListSessionsQuery listSessionsQuery(IdpAdminPort idpAdminPort) {
    return new ListSessionsService(idpAdminPort);
  }

  @Bean
  RevokeSessionUseCase revokeSessionUseCase(
      IdpAdminPort idpAdminPort, AuditEventPort auditEventPort) {
    return new RevokeSessionService(idpAdminPort, auditEventPort);
  }
}
