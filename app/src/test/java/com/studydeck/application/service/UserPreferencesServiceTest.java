package com.studydeck.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.studydeck.application.exception.NotFoundException;
import com.studydeck.application.support.InMemoryAuditEventPort;
import com.studydeck.application.support.InMemoryUserAccountRepository;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.UserAccount;
import com.studydeck.domain.port.in.UpdateUserPreferencesUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link UserPreferencesService}. */
class UserPreferencesServiceTest {

  private InMemoryUserAccountRepository userRepo;
  private UserPreferencesService sut;

  private final OwnerId alice = OwnerId.generate();

  @BeforeEach
  void setUp() {
    userRepo = new InMemoryUserAccountRepository();
    sut = new UserPreferencesService(userRepo, new InMemoryAuditEventPort());
  }

  @Test
  @DisplayName("updates the daily goal for an existing user")
  void updatesDailyGoal() {
    userRepo.save(UserAccount.provision(alice, "alice@test.com", "Alice"));

    sut.execute(new UpdateUserPreferencesUseCase.Command(alice, 25, null, null, null, null));

    assertThat(userRepo.findById(alice).orElseThrow().getDailyGoal()).isEqualTo(25);
  }

  @Test
  @DisplayName("throws NotFoundException when the user is not provisioned")
  void throwsWhenUserMissing() {
    assertThatThrownBy(
            () ->
                sut.execute(
                    new UpdateUserPreferencesUseCase.Command(alice, 30, null, null, null, null)))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  @DisplayName("updates desiredRetention for an existing user")
  void updatesDesiredRetention() {
    userRepo.save(UserAccount.provision(alice, "alice@test.com", "Alice"));

    sut.execute(new UpdateUserPreferencesUseCase.Command(alice, null, 0.80, null, null, null));

    assertThat(userRepo.findById(alice).orElseThrow().getDesiredRetention()).isEqualTo(0.80);
  }

  @Test
  @DisplayName("updates language to es for an existing user")
  void updatesLanguage() {
    userRepo.save(UserAccount.provision(alice, "alice@test.com", "Alice"));

    sut.execute(new UpdateUserPreferencesUseCase.Command(alice, null, null, null, "es", null));

    assertThat(userRepo.findById(alice).orElseThrow().getLanguage()).isEqualTo("es");
  }

  @Test
  @DisplayName("partial update with only dailyGoal does not change other preference fields")
  void partialUpdate_onlyDailyGoal_doesNotChangeOtherFields() {
    UserAccount original = UserAccount.provision(alice, "alice@test.com", "Alice");
    userRepo.save(original);
    double originalRetention = original.getDesiredRetention();
    int originalNewCards = original.getNewCardsPerDay();
    String originalLanguage = original.getLanguage();
    String originalTimezone = original.getTimezone();

    sut.execute(new UpdateUserPreferencesUseCase.Command(alice, 50, null, null, null, null));

    UserAccount updated = userRepo.findById(alice).orElseThrow();
    assertThat(updated.getDailyGoal()).isEqualTo(50);
    assertThat(updated.getDesiredRetention()).isEqualTo(originalRetention);
    assertThat(updated.getNewCardsPerDay()).isEqualTo(originalNewCards);
    assertThat(updated.getLanguage()).isEqualTo(originalLanguage);
    assertThat(updated.getTimezone()).isEqualTo(originalTimezone);
  }

  @Test
  @DisplayName("rejects command with null ownerId")
  void rejectsNullOwner() {
    assertThatThrownBy(
            () -> new UpdateUserPreferencesUseCase.Command(null, null, null, null, null, null))
        .isInstanceOf(NullPointerException.class);
  }
}
