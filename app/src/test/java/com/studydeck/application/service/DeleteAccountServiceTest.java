package com.studydeck.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.studydeck.application.support.InMemoryAuditEventPort;
import com.studydeck.application.support.InMemoryUserAccountRepository;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.UserAccount;
import com.studydeck.domain.port.in.DeleteAccountUseCase;
import com.studydeck.domain.port.out.IdpAdminPort;
import com.studydeck.domain.port.out.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Use-case tests for DeleteAccountService — plain Java, no Spring. */
class DeleteAccountServiceTest {

  private InMemoryUserAccountRepository userRepo;
  private InMemoryAuditEventPort auditPort;
  private IdpAdminPort idpAdminPort;
  private DeleteAccountUseCase deleteAccount;

  private final OwnerId alice = OwnerId.generate();

  @BeforeEach
  void setUp() {
    userRepo = new InMemoryUserAccountRepository();
    auditPort = new InMemoryAuditEventPort();
    idpAdminPort = mock(IdpAdminPort.class);
    deleteAccount = new DeleteAccountService(userRepo, auditPort, idpAdminPort);
  }

  @Test
  @DisplayName("records an audit event after deleting the account")
  void recordsAuditEventAfterDelete() {
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
    deleteAccount.execute(ghost);
    assertThat(auditPort.hasAction("account.delete")).isTrue();
  }

  @Test
  @DisplayName("audit event is NOT recorded when deleteById throws — no false audit trail")
  void noAuditWhenDeleteThrows() {
    UserAccountRepository failingRepo =
        new UserAccountRepository() {
          @Override
          public java.util.Optional<UserAccount> findById(OwnerId id) {
            return java.util.Optional.empty();
          }

          @Override
          public void save(UserAccount userAccount) {}

          @Override
          public boolean existsById(OwnerId id) {
            return false;
          }

          @Override
          public void deleteById(OwnerId id) {
            throw new RuntimeException("simulated DB failure");
          }
        };

    InMemoryAuditEventPort freshAudit = new InMemoryAuditEventPort();
    IdpAdminPort freshIdp = mock(IdpAdminPort.class);
    DeleteAccountUseCase failingDelete =
        new DeleteAccountService(failingRepo, freshAudit, freshIdp);

    assertThatThrownBy(() -> failingDelete.execute(alice)).isInstanceOf(RuntimeException.class);

    assertThat(freshAudit.hasAction("account.delete")).isFalse();
  }

  @Test
  @DisplayName("audit event is recorded AFTER successful delete (delete-then-audit order)")
  void auditRecordedAfterDeleteSucceeds() {
    UserAccount account = UserAccount.provision(alice, "alice@example.com", "Alice");
    userRepo.save(account);

    deleteAccount.execute(alice);

    assertThat(userRepo.existsById(alice)).isFalse();
    assertThat(auditPort.hasAction("account.delete")).isTrue();
  }

  @Test
  @DisplayName("calls idpAdminPort.deleteUser with the owner's id string after app data deletion")
  void callsIdpDeleteUserAfterAppDataDeletion() {
    UserAccount account = UserAccount.provision(alice, "alice@example.com", "Alice");
    userRepo.save(account);

    deleteAccount.execute(alice);

    verify(idpAdminPort).deleteUser(alice.value().toString());
    // App data was also deleted
    assertThat(userRepo.existsById(alice)).isFalse();
  }

  @Test
  @DisplayName("app data deletion succeeds even when idpAdminPort.deleteUser throws (best-effort)")
  void appDataDeletedEvenWhenIdpThrows() {
    UserAccount account = UserAccount.provision(alice, "alice@example.com", "Alice");
    userRepo.save(account);

    doThrow(new RuntimeException("IdP unavailable"))
        .when(idpAdminPort)
        .deleteUser(alice.value().toString());

    // Should NOT throw — best-effort IdP deletion
    deleteAccount.execute(alice);

    // App data was deleted
    assertThat(userRepo.existsById(alice)).isFalse();
    // Audit was recorded
    assertThat(auditPort.hasAction("account.delete")).isTrue();
  }
}
