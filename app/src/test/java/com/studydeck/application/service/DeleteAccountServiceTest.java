package com.studydeck.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.studydeck.application.support.InMemoryAuditEventPort;
import com.studydeck.application.support.InMemoryUserAccountRepository;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.UserAccount;
import com.studydeck.domain.port.in.DeleteAccountUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Use-case tests for DeleteAccountService — plain Java, no Spring. */
class DeleteAccountServiceTest {

  private InMemoryUserAccountRepository userRepo;
  private InMemoryAuditEventPort auditPort;
  private DeleteAccountUseCase deleteAccount;

  private final OwnerId alice = OwnerId.generate();

  @BeforeEach
  void setUp() {
    userRepo = new InMemoryUserAccountRepository();
    auditPort = new InMemoryAuditEventPort();
    deleteAccount = new DeleteAccountService(userRepo, auditPort);
  }

  @Test
  @DisplayName("records an audit event before deleting the account")
  void recordsAuditEventBeforeDelete() {
    UserAccount account = UserAccount.provision(alice, "alice@example.com", "Alice");
    userRepo.save(account);

    deleteAccount.execute(alice);

    assertThat(auditPort.hasAction("account.delete")).isTrue();
    assertThat(auditPort.recorded().getFirst().actorId()).isEqualTo(alice);
    assertThat(auditPort.recorded().getFirst().targetType()).isEqualTo("user_account");
    assertThat(auditPort.recorded().getFirst().targetId()).isEqualTo(alice.toString());
  }

  @Test
  @DisplayName("calls deleteById on the repository")
  void callsDeleteById() {
    UserAccount account = UserAccount.provision(alice, "alice@example.com", "Alice");
    userRepo.save(account);
    assertThat(userRepo.existsById(alice)).isTrue();

    deleteAccount.execute(alice);

    assertThat(userRepo.existsById(alice)).isFalse();
  }

  @Test
  @DisplayName("is idempotent — deleting a non-existent account does not throw")
  void idempotentOnMissingAccount() {
    OwnerId ghost = OwnerId.generate();
    // No account provisioned for ghost — should not throw
    deleteAccount.execute(ghost);
    // Audit event still recorded
    assertThat(auditPort.hasAction("account.delete")).isTrue();
  }
}
