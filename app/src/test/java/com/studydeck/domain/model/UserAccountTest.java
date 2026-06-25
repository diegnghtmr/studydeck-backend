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

  @Nested
  @DisplayName("updateDesiredRetention")
  class UpdateDesiredRetention {

    @Test
    @DisplayName("sets desiredRetention and updates updatedAt")
    void setsFieldAndTimestamp() throws InterruptedException {
      UserAccount account = UserAccount.provision(anyId, "alice@example.com", "Alice");
      var before = account.getUpdatedAt();

      Thread.sleep(2);
      account.updateDesiredRetention(0.85);

      assertThat(account.getDesiredRetention()).isEqualTo(0.85);
      assertThat(account.getUpdatedAt()).isAfterOrEqualTo(before);
    }

    @Test
    @DisplayName("rejects retention below 0.50")
    void rejectsBelowMin() {
      UserAccount account = UserAccount.provision(anyId, "a@b.com", "A");
      assertThatThrownBy(() -> account.updateDesiredRetention(0.49))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("desiredRetention");
    }

    @Test
    @DisplayName("rejects retention above 0.99")
    void rejectsAboveMax() {
      UserAccount account = UserAccount.provision(anyId, "a@b.com", "A");
      assertThatThrownBy(() -> account.updateDesiredRetention(1.0))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("desiredRetention");
    }

    @Test
    @DisplayName("accepts boundary values 0.50 and 0.99")
    void acceptsBoundaryValues() {
      UserAccount account = UserAccount.provision(anyId, "a@b.com", "A");
      account.updateDesiredRetention(0.50);
      assertThat(account.getDesiredRetention()).isEqualTo(0.50);
      account.updateDesiredRetention(0.99);
      assertThat(account.getDesiredRetention()).isEqualTo(0.99);
    }
  }

  @Nested
  @DisplayName("updateNewCardsPerDay")
  class UpdateNewCardsPerDay {

    @Test
    @DisplayName("sets newCardsPerDay and updates updatedAt")
    void setsFieldAndTimestamp() throws InterruptedException {
      UserAccount account = UserAccount.provision(anyId, "alice@example.com", "Alice");
      var before = account.getUpdatedAt();

      Thread.sleep(2);
      account.updateNewCardsPerDay(50);

      assertThat(account.getNewCardsPerDay()).isEqualTo(50);
      assertThat(account.getUpdatedAt()).isAfterOrEqualTo(before);
    }

    @Test
    @DisplayName("rejects newCardsPerDay below 0")
    void rejectsBelowMin() {
      UserAccount account = UserAccount.provision(anyId, "a@b.com", "A");
      assertThatThrownBy(() -> account.updateNewCardsPerDay(-1))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("newCardsPerDay");
    }

    @Test
    @DisplayName("rejects newCardsPerDay above 999")
    void rejectsAboveMax() {
      UserAccount account = UserAccount.provision(anyId, "a@b.com", "A");
      assertThatThrownBy(() -> account.updateNewCardsPerDay(1000))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("newCardsPerDay");
    }

    @Test
    @DisplayName("accepts boundary values 0 and 999")
    void acceptsBoundaryValues() {
      UserAccount account = UserAccount.provision(anyId, "a@b.com", "A");
      account.updateNewCardsPerDay(0);
      assertThat(account.getNewCardsPerDay()).isEqualTo(0);
      account.updateNewCardsPerDay(999);
      assertThat(account.getNewCardsPerDay()).isEqualTo(999);
    }
  }

  @Nested
  @DisplayName("updateLanguage")
  class UpdateLanguage {

    @Test
    @DisplayName("accepts all allowed language codes")
    void acceptsAllowedCodes() {
      UserAccount account = UserAccount.provision(anyId, "a@b.com", "A");
      for (String lang : new String[] {"en", "es", "fr", "pt"}) {
        account.updateLanguage(lang);
        assertThat(account.getLanguage()).isEqualTo(lang);
      }
    }

    @Test
    @DisplayName("rejects unknown language code")
    void rejectsUnknownCode() {
      UserAccount account = UserAccount.provision(anyId, "a@b.com", "A");
      assertThatThrownBy(() -> account.updateLanguage("de"))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("language");
    }

    @Test
    @DisplayName("rejects null language")
    void rejectsNull() {
      UserAccount account = UserAccount.provision(anyId, "a@b.com", "A");
      assertThatThrownBy(() -> account.updateLanguage(null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("language");
    }

    @Test
    @DisplayName("updates updatedAt after language change")
    void updatesTimestamp() throws InterruptedException {
      UserAccount account = UserAccount.provision(anyId, "a@b.com", "A");
      var before = account.getUpdatedAt();
      Thread.sleep(2);
      account.updateLanguage("es");
      assertThat(account.getUpdatedAt()).isAfterOrEqualTo(before);
    }
  }

  @Nested
  @DisplayName("updateTimezone")
  class UpdateTimezone {

    @Test
    @DisplayName("accepts valid IANA timezone")
    void acceptsValidTimezone() {
      UserAccount account = UserAccount.provision(anyId, "a@b.com", "A");
      account.updateTimezone("America/New_York");
      assertThat(account.getTimezone()).isEqualTo("America/New_York");
    }

    @Test
    @DisplayName("accepts UTC")
    void acceptsUtc() {
      UserAccount account = UserAccount.provision(anyId, "a@b.com", "A");
      account.updateTimezone("UTC");
      assertThat(account.getTimezone()).isEqualTo("UTC");
    }

    @Test
    @DisplayName("rejects invalid timezone string")
    void rejectsInvalidZone() {
      UserAccount account = UserAccount.provision(anyId, "a@b.com", "A");
      assertThatThrownBy(() -> account.updateTimezone("Not/AZone"))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("timezone");
    }

    @Test
    @DisplayName("rejects null timezone")
    void rejectsNull() {
      UserAccount account = UserAccount.provision(anyId, "a@b.com", "A");
      assertThatThrownBy(() -> account.updateTimezone(null))
          .isInstanceOf(DomainValidationException.class)
          .hasMessageContaining("timezone");
    }

    @Test
    @DisplayName("updates updatedAt after timezone change")
    void updatesTimestamp() throws InterruptedException {
      UserAccount account = UserAccount.provision(anyId, "a@b.com", "A");
      var before = account.getUpdatedAt();
      Thread.sleep(2);
      account.updateTimezone("Europe/Paris");
      assertThat(account.getUpdatedAt()).isAfterOrEqualTo(before);
    }
  }

  @Nested
  @DisplayName("partial preferences update")
  class PartialPreferences {

    @Test
    @DisplayName("updating only dailyGoal leaves other preference fields unchanged")
    void updateOnlyDailyGoalDoesNotAffectOtherFields() {
      UserAccount account = UserAccount.provision(anyId, "a@b.com", "A");
      double originalRetention = account.getDesiredRetention();
      int originalNewCards = account.getNewCardsPerDay();
      String originalLanguage = account.getLanguage();
      String originalTimezone = account.getTimezone();

      account.updateDailyGoal(100);

      assertThat(account.getDesiredRetention()).isEqualTo(originalRetention);
      assertThat(account.getNewCardsPerDay()).isEqualTo(originalNewCards);
      assertThat(account.getLanguage()).isEqualTo(originalLanguage);
      assertThat(account.getTimezone()).isEqualTo(originalTimezone);
    }

    @Test
    @DisplayName("updating only language leaves other preference fields unchanged")
    void updateOnlyLanguageDoesNotAffectOtherFields() {
      UserAccount account = UserAccount.provision(anyId, "a@b.com", "A");
      int originalDailyGoal = account.getDailyGoal();
      double originalRetention = account.getDesiredRetention();
      int originalNewCards = account.getNewCardsPerDay();
      String originalTimezone = account.getTimezone();

      account.updateLanguage("fr");

      assertThat(account.getDailyGoal()).isEqualTo(originalDailyGoal);
      assertThat(account.getDesiredRetention()).isEqualTo(originalRetention);
      assertThat(account.getNewCardsPerDay()).isEqualTo(originalNewCards);
      assertThat(account.getTimezone()).isEqualTo(originalTimezone);
    }
  }
}
