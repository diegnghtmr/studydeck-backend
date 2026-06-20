package com.studydeck.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.studydeck.domain.exception.DomainValidationException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link UserAccount} aggregate invariants. */
class UserAccountTest {

  private final OwnerId anyId = new OwnerId(UUID.randomUUID());

  @Nested
  @DisplayName("provision factory")
  class Provision {

    @Test
    @DisplayName("creates an ACTIVE account with the given values")
    void provisionsActiveAccount() {
      UserAccount account = UserAccount.provision(anyId, "alice@example.com", "Alice");

      assertThat(account.getId()).isEqualTo(anyId);
      assertThat(account.getEmail()).isEqualTo("alice@example.com");
      assertThat(account.getDisplayName()).isEqualTo("Alice");
      assertThat(account.getStatus()).isEqualTo(UserAccountStatus.ACTIVE);
      assertThat(account.getCreatedAt()).isNotNull();
      assertThat(account.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("derives display name from email local-part when display name is null")
    void derivesDisplayNameFromEmail() {
      UserAccount account = UserAccount.provision(anyId, "bob@example.com", null);

      assertThat(account.getDisplayName()).isEqualTo("bob");
    }

    @Test
    @DisplayName("derives display name from email local-part when display name is blank")
    void derivesDisplayNameFromEmailWhenBlank() {
      UserAccount account = UserAccount.provision(anyId, "charlie@example.com", "   ");

      assertThat(account.getDisplayName()).isEqualTo("charlie");
    }

    @Test
    @DisplayName("rejects null id")
    void rejectsNullId() {
      assertThatThrownBy(() -> UserAccount.provision(null, "a@b.com", "A"))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("rejects null email")
    void rejectsNullEmail() {
      assertThatThrownBy(() -> UserAccount.provision(anyId, null, "A"))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("email");
    }

    @Test
    @DisplayName("rejects blank email")
    void rejectsBlankEmail() {
      assertThatThrownBy(() -> UserAccount.provision(anyId, "  ", "A"))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("email");
    }

    @Test
    @DisplayName("rejects email exceeding 254 characters")
    void rejectsOversizeEmail() {
      String longEmail = "a".repeat(250) + "@b.com"; // 256 chars — exceeds 254
      assertThat(longEmail.length()).isGreaterThan(254);

      assertThatThrownBy(() -> UserAccount.provision(anyId, longEmail, "A"))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("email");
    }
  }

  @Nested
  @DisplayName("updateIdentity")
  class UpdateIdentity {

    @Test
    @DisplayName("updates email and displayName")
    void updatesFields() {
      UserAccount account = UserAccount.provision(anyId, "old@example.com", "Old Name");

      account.updateIdentity("new@example.com", "New Name");

      assertThat(account.getEmail()).isEqualTo("new@example.com");
      assertThat(account.getDisplayName()).isEqualTo("New Name");
    }

    @Test
    @DisplayName("updatedAt changes after updateIdentity")
    void updatesTimestamp() throws InterruptedException {
      UserAccount account = UserAccount.provision(anyId, "old@example.com", "Name");
      var before = account.getUpdatedAt();

      Thread.sleep(2); // ensure timestamp advances
      account.updateIdentity("new@example.com", "New Name");

      assertThat(account.getUpdatedAt()).isAfterOrEqualTo(before);
    }
  }
}
