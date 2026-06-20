package com.studydeck.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.studydeck.application.support.InMemoryAuditEventPort;
import com.studydeck.application.support.InMemoryUserAccountRepository;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.in.ProvisionUserUseCase;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link ProvisionUserService}.
 *
 * <p>No Spring context — ports are backed by in-memory doubles.
 */
class ProvisionUserServiceTest {

  private InMemoryUserAccountRepository userRepo;
  private InMemoryAuditEventPort auditPort;
  private ProvisionUserService sut;

  @BeforeEach
  void setUp() {
    userRepo = new InMemoryUserAccountRepository();
    auditPort = new InMemoryAuditEventPort();
    sut = new ProvisionUserService(userRepo, auditPort);
  }

  @Nested
  @DisplayName("first provisioning (no existing row)")
  class FirstProvisioning {

    @Test
    @DisplayName("inserts a new user_account row")
    void insertsRow() {
      OwnerId userId = new OwnerId(UUID.randomUUID());
      var cmd = new ProvisionUserUseCase.Command(userId, "alice@example.com", "Alice");

      sut.execute(cmd);

      assertThat(userRepo.size()).isEqualTo(1);
      assertThat(userRepo.findById(userId)).isPresent();
    }

    @Test
    @DisplayName("emits a user.provisioned audit event")
    void emitsAuditEvent() {
      OwnerId userId = new OwnerId(UUID.randomUUID());
      var cmd = new ProvisionUserUseCase.Command(userId, "alice@example.com", "Alice");

      sut.execute(cmd);

      assertThat(auditPort.size()).isEqualTo(1);
      assertThat(auditPort.hasAction("user.provisioned")).isTrue();
    }
  }

  @Nested
  @DisplayName("idempotency (user_account already exists)")
  class Idempotency {

    @Test
    @DisplayName("does not create a duplicate row on second call")
    void noDuplicateOnSecondCall() {
      OwnerId userId = new OwnerId(UUID.randomUUID());
      var cmd = new ProvisionUserUseCase.Command(userId, "bob@example.com", "Bob");

      sut.execute(cmd);
      sut.execute(cmd); // second call — same subject

      assertThat(userRepo.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("emits audit event only on first provisioning, not on subsequent calls")
    void auditEventOnlyOnce() {
      OwnerId userId = new OwnerId(UUID.randomUUID());
      var cmd = new ProvisionUserUseCase.Command(userId, "carol@example.com", "Carol");

      sut.execute(cmd);
      sut.execute(cmd); // idempotent — should not re-audit

      assertThat(auditPort.size()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("command validation")
  class CommandValidation {

    @Test
    @DisplayName("rejects null userId")
    void rejectsNullUserId() {
      assertThatThrownBy(() -> new ProvisionUserUseCase.Command(null, "a@b.com", "Name"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("userId");
    }

    @Test
    @DisplayName("rejects blank email")
    void rejectsBlankEmail() {
      assertThatThrownBy(
              () -> new ProvisionUserUseCase.Command(new OwnerId(UUID.randomUUID()), "  ", "Name"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("email");
    }
  }
}
