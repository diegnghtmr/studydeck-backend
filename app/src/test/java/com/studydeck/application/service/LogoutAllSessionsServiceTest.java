package com.studydeck.application.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.in.LogoutAllSessionsUseCase;
import com.studydeck.domain.port.out.IdpAdminPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for LogoutAllSessionsService — plain Java, no Spring. */
class LogoutAllSessionsServiceTest {

  private IdpAdminPort idpAdminPort;
  private LogoutAllSessionsUseCase logoutAllSessions;

  @BeforeEach
  void setUp() {
    idpAdminPort = mock(IdpAdminPort.class);
    logoutAllSessions = new LogoutAllSessionsService(idpAdminPort);
  }

  @Test
  @DisplayName("delegates to idpAdminPort.logoutAllSessions with owner's id string")
  void delegatesToIdpAdminPort() {
    OwnerId owner = OwnerId.generate();

    logoutAllSessions.execute(owner);

    verify(idpAdminPort).logoutAllSessions(owner.value().toString());
  }
}
