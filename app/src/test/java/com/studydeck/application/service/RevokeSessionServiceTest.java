package com.studydeck.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.studydeck.application.exception.NotFoundException;
import com.studydeck.application.support.InMemoryAuditEventPort;
import com.studydeck.domain.model.IdpSession;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.in.RevokeSessionUseCase;
import com.studydeck.domain.port.out.IdpAdminPort;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for RevokeSessionService — plain Java, no Spring. */
class RevokeSessionServiceTest {

  private IdpAdminPort idpAdminPort;
  private InMemoryAuditEventPort auditPort;
  private RevokeSessionUseCase revokeSession;

  private final OwnerId alice = OwnerId.generate();

  @BeforeEach
  void setUp() {
    idpAdminPort = mock(IdpAdminPort.class);
    auditPort = new InMemoryAuditEventPort();
    revokeSession = new RevokeSessionService(idpAdminPort, auditPort);
  }

  @Test
  @DisplayName("revokes an owned session and records audit")
  void revokesOwnedSession() {
    IdpSession session =
        new IdpSession("sess-123", "10.0.0.1", Instant.now(), Instant.now(), List.of("web"));
    when(idpAdminPort.listSessions(alice.value().toString())).thenReturn(List.of(session));

    revokeSession.execute(new RevokeSessionUseCase.Command(alice, "sess-123"));

    verify(idpAdminPort).revokeSession("sess-123");
    assertThat(auditPort.hasAction("session.revoke")).isTrue();
  }

  @Test
  @DisplayName("throws NotFoundException when session not owned by user — IDOR protection")
  void throwsNotFoundWhenSessionNotOwned() {
    when(idpAdminPort.listSessions(alice.value().toString())).thenReturn(List.of());

    assertThatThrownBy(
            () -> revokeSession.execute(new RevokeSessionUseCase.Command(alice, "other-user-sess")))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("other-user-sess");

    verify(idpAdminPort, never()).revokeSession("other-user-sess");
  }

  @Test
  @DisplayName("does not revoke when user has sessions but none match sessionId")
  void throwsNotFoundWhenSessionIdNotInUserList() {
    IdpSession ownedSession =
        new IdpSession("sess-abc", "10.0.0.1", Instant.now(), Instant.now(), List.of());
    when(idpAdminPort.listSessions(alice.value().toString())).thenReturn(List.of(ownedSession));

    assertThatThrownBy(
            () -> revokeSession.execute(new RevokeSessionUseCase.Command(alice, "sess-xyz")))
        .isInstanceOf(NotFoundException.class);

    verify(idpAdminPort, never()).revokeSession("sess-xyz");
  }
}
