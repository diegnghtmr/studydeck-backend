package com.studydeck.application.service;

import com.studydeck.application.exception.NotFoundException;
import com.studydeck.domain.model.IdpSession;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.in.RevokeSessionUseCase;
import com.studydeck.domain.port.out.AuditEventPort;
import com.studydeck.domain.port.out.IdpAdminPort;
import java.util.List;

/**
 * Application service: revokes a specific Identity Provider session.
 *
 * <p>Security: verifies that the session belongs to the requesting user before revoking. If the
 * session is not found in the user's session list, throws {@link NotFoundException} — this also
 * prevents users from revoking other users' sessions (IDOR protection).
 *
 * <p>Framework-free: no Spring annotations. Wired as {@code @Bean} in {@link
 * com.studydeck.infrastructure.config.IdpAdminConfiguration}.
 */
public final class RevokeSessionService implements RevokeSessionUseCase {

  private final IdpAdminPort idpAdminPort;
  private final AuditEventPort auditPort;

  public RevokeSessionService(IdpAdminPort idpAdminPort, AuditEventPort auditPort) {
    this.idpAdminPort = idpAdminPort;
    this.auditPort = auditPort;
  }

  @Override
  public void execute(Command command) {
    OwnerId ownerId = command.ownerId();
    String sessionId = command.sessionId();

    // SECURITY: verify the session belongs to the requesting user
    List<IdpSession> sessions = idpAdminPort.listSessions(ownerId.value().toString());
    boolean owned = sessions.stream().anyMatch(s -> s.id().equals(sessionId));
    if (!owned) {
      throw new NotFoundException("session", sessionId);
    }

    idpAdminPort.revokeSession(sessionId);
    auditPort.record(ownerId, "session.revoke", "idp_session", sessionId);
  }
}
