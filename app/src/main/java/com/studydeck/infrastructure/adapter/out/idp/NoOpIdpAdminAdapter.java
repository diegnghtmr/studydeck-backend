package com.studydeck.infrastructure.adapter.out.idp;

import com.studydeck.domain.model.IdpSession;
import com.studydeck.domain.port.out.IdpAdminPort;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * No-op implementation of {@link IdpAdminPort}.
 *
 * <p>Used when Keycloak admin is not configured (e.g., dev HS256 profile, local Docker Compose
 * without IdP, unit tests). Logs a warning and does nothing — ensuring the app boots and existing
 * features work without Keycloak admin credentials.
 */
class NoOpIdpAdminAdapter implements IdpAdminPort {

  private static final Logger log = LoggerFactory.getLogger(NoOpIdpAdminAdapter.class);

  @Override
  public void deleteUser(String idpUserId) {
    log.warn(
        "IdP admin not configured — skipping user deletion for idpUserId={}. "
            + "Set studydeck.idp.admin.base-url to enable.",
        idpUserId);
  }

  @Override
  public void logoutAllSessions(String idpUserId) {
    log.warn(
        "IdP admin not configured — skipping session logout for idpUserId={}. "
            + "Set studydeck.idp.admin.base-url to enable.",
        idpUserId);
  }

  @Override
  public List<IdpSession> listSessions(String idpUserId) {
    log.warn(
        "IdP admin not configured — returning empty session list for idpUserId={}. "
            + "Set studydeck.idp.admin.base-url to enable.",
        idpUserId);
    return List.of();
  }

  @Override
  public void revokeSession(String sessionId) {
    log.warn(
        "IdP admin not configured — skipping session revoke for sessionId={}. "
            + "Set studydeck.idp.admin.base-url to enable.",
        sessionId);
  }
}
