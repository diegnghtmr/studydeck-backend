package com.studydeck.application.service;

import com.studydeck.domain.model.IdpSession;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.in.ListSessionsQuery;
import com.studydeck.domain.port.out.IdpAdminPort;
import java.util.List;

/**
 * Application service: lists active Identity Provider sessions for a user.
 *
 * <p>Framework-free: no Spring annotations. Wired as {@code @Bean} in {@link
 * com.studydeck.infrastructure.config.IdpAdminConfiguration}.
 */
public final class ListSessionsService implements ListSessionsQuery {

  private final IdpAdminPort idpAdminPort;

  public ListSessionsService(IdpAdminPort idpAdminPort) {
    this.idpAdminPort = idpAdminPort;
  }

  @Override
  public List<IdpSession> execute(Query query) {
    OwnerId ownerId = query.ownerId();
    return idpAdminPort.listSessions(ownerId.value().toString());
  }
}
