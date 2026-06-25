package com.studydeck.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.studydeck.domain.model.IdpSession;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.in.ListSessionsQuery;
import com.studydeck.domain.port.out.IdpAdminPort;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for ListSessionsService — plain Java, no Spring. */
class ListSessionsServiceTest {

  private IdpAdminPort idpAdminPort;
  private ListSessionsQuery listSessions;

  @BeforeEach
  void setUp() {
    idpAdminPort = mock(IdpAdminPort.class);
    listSessions = new ListSessionsService(idpAdminPort);
  }

  @Test
  @DisplayName("delegates to idpAdminPort.listSessions with owner's id string")
  void delegatesToIdpAdminPort() {
    OwnerId owner = OwnerId.generate();
    List<IdpSession> expected =
        List.of(
            new IdpSession(
                "session-1", "127.0.0.1", Instant.now(), Instant.now(), List.of("studydeck")));
    when(idpAdminPort.listSessions(owner.value().toString())).thenReturn(expected);

    List<IdpSession> result = listSessions.execute(new ListSessionsQuery.Query(owner));

    assertThat(result).isEqualTo(expected);
    verify(idpAdminPort).listSessions(owner.value().toString());
  }

  @Test
  @DisplayName("returns empty list when port returns empty")
  void returnsEmptyListWhenNoSessions() {
    OwnerId owner = OwnerId.generate();
    when(idpAdminPort.listSessions(owner.value().toString())).thenReturn(List.of());

    List<IdpSession> result = listSessions.execute(new ListSessionsQuery.Query(owner));

    assertThat(result).isEmpty();
  }
}
