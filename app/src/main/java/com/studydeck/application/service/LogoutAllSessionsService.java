package com.studydeck.application.service;

import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.in.LogoutAllSessionsUseCase;
import com.studydeck.domain.port.out.IdpAdminPort;

/**
 * Application service: revokes all Identity Provider sessions for a user.
 *
 * <p>Framework-free: no Spring annotations. Wired as {@code @Bean} in {@link
 * com.studydeck.infrastructure.config.IdpAdminConfiguration}.
 */
public final class LogoutAllSessionsService implements LogoutAllSessionsUseCase {

  private final IdpAdminPort idpAdminPort;

  public LogoutAllSessionsService(IdpAdminPort idpAdminPort) {
    this.idpAdminPort = idpAdminPort;
  }

  @Override
  public void execute(OwnerId ownerId) {
    idpAdminPort.logoutAllSessions(ownerId.value().toString());
  }
}
