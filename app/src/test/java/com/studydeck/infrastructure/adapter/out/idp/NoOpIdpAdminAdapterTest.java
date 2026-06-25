package com.studydeck.infrastructure.adapter.out.idp;

import com.studydeck.domain.port.out.IdpAdminPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit test for NoOpIdpAdminAdapter — verifies it does nothing and does not throw. */
class NoOpIdpAdminAdapterTest {

  private final IdpAdminPort adapter = new NoOpIdpAdminAdapter();

  @Test
  @DisplayName("deleteUser does not throw")
  void deleteUserDoesNotThrow() {
    adapter.deleteUser("any-user-id");
  }

  @Test
  @DisplayName("logoutAllSessions does not throw")
  void logoutAllSessionsDoesNotThrow() {
    adapter.logoutAllSessions("any-user-id");
  }
}
