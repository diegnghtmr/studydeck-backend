package com.studydeck.application.service;

import com.studydeck.application.exception.NotFoundException;
import com.studydeck.domain.model.UserAccount;
import com.studydeck.domain.port.in.UpdateUserPreferencesUseCase;
import com.studydeck.domain.port.out.AuditEventPort;
import com.studydeck.domain.port.out.UserAccountRepository;

/**
 * Application service implementing {@link UpdateUserPreferencesUseCase}.
 *
 * <p>Kept separate from {@code ProvisionUserService} so the two user-account use cases register as
 * distinct beans (a single class implementing both would make {@code ProvisionUserUseCase}
 * ambiguous by type).
 *
 * <p>Framework-free: no Spring annotations. Wired as {@code @Bean} in {@code BeanConfiguration}.
 *
 * <p>Partial-update semantics: only non-null fields in the command are applied to the account.
 */
public final class UserPreferencesService implements UpdateUserPreferencesUseCase {

  private final UserAccountRepository userAccountRepository;
  private final AuditEventPort auditEventPort;

  public UserPreferencesService(
      UserAccountRepository userAccountRepository, AuditEventPort auditEventPort) {
    this.userAccountRepository = userAccountRepository;
    this.auditEventPort = auditEventPort;
  }

  @Override
  public void execute(Command command) {
    UserAccount account =
        userAccountRepository
            .findById(command.ownerId())
            .orElseThrow(() -> new NotFoundException("UserAccount", command.ownerId().toString()));

    if (command.dailyGoal() != null) {
      account.updateDailyGoal(command.dailyGoal());
    }
    if (command.desiredRetention() != null) {
      account.updateDesiredRetention(command.desiredRetention());
    }
    if (command.newCardsPerDay() != null) {
      account.updateNewCardsPerDay(command.newCardsPerDay());
    }
    if (command.language() != null) {
      account.updateLanguage(command.language());
    }
    if (command.timezone() != null) {
      account.updateTimezone(command.timezone());
    }
    if (command.schedulerAlgorithm() != null) {
      account.updateSchedulerAlgorithm(command.schedulerAlgorithm());
    }

    userAccountRepository.save(account);
    auditEventPort.record(
        command.ownerId(), "user.preferences.updated", "UserAccount", command.ownerId().toString());
  }
}
