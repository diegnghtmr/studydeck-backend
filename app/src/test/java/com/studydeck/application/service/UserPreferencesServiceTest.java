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

    sut.execute(new UpdateUserPreferencesUseCase.Command(alice, 25));

    assertThat(userRepo.findById(alice).orElseThrow().getDailyGoal()).isEqualTo(25);
  }

  @Test
  @DisplayName("throws NotFoundException when the user is not provisioned")
  void throwsWhenUserMissing() {
    assertThatThrownBy(() -> sut.execute(new UpdateUserPreferencesUseCase.Command(alice, 30)))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  @DisplayName("rejects an out-of-range daily goal at the command boundary")
  void rejectsOutOfRange() {
    assertThatThrownBy(() -> new UpdateUserPreferencesUseCase.Command(alice, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new UpdateUserPreferencesUseCase.Command(alice, 1001))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
