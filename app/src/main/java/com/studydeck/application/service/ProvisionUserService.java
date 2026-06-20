package com.studydeck.application.service;

import com.studydeck.domain.model.UserAccount;
import com.studydeck.domain.port.in.ProvisionUserUseCase;
import com.studydeck.domain.port.out.AuditEventPort;
import com.studydeck.domain.port.out.UserAccountRepository;

/**
 * Application service implementing {@link ProvisionUserUseCase}.
 *
 * <p>JIT (just-in-time) user provisioning: on the first authenticated request for a given JWT
 * subject, inserts a {@code user_account} row so downstream FK constraints on {@code deck.owner_id}
 * and {@code audit_event.actor_id} are satisfied. Subsequent calls with the same subject are cheap
 * (existence check only — no write).
 *
 * <p>Framework-free: no Spring annotations. Wired as {@code @Bean} in {@code BeanConfiguration}.
 */
public final class ProvisionUserService implements ProvisionUserUseCase {

  private final UserAccountRepository userAccountRepository;
  private final AuditEventPort auditEventPort;

  public ProvisionUserService(
      UserAccountRepository userAccountRepository, AuditEventPort auditEventPort) {
    this.userAccountRepository = userAccountRepository;
    this.auditEventPort = auditEventPort;
  }

  @Override
  public void execute(Command command) {
    if (userAccountRepository.existsById(command.userId())) {
      // Happy path: user already provisioned — avoid unnecessary write on every request
      return;
    }

    UserAccount account =
        UserAccount.provision(command.userId(), command.email(), command.displayName());
    userAccountRepository.save(account);
    auditEventPort.record(
        command.userId(), "user.provisioned", "UserAccount", command.userId().toString());
  }
}
