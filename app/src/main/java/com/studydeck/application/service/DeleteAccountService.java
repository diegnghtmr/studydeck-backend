package com.studydeck.application.service;

import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.in.DeleteAccountUseCase;
import com.studydeck.domain.port.out.AuditEventPort;
import com.studydeck.domain.port.out.UserAccountRepository;

/**
 * Application service implementing GDPR right to erasure.
 *
 * <p>Records an audit event BEFORE deleting so that there is a tamper-evident record of who
 * requested deletion. The database cascade (ON DELETE CASCADE) removes all child rows automatically
 * once the {@code user_account} row is deleted.
 *
 * <p>Framework-free: no Spring annotations. Wired as {@code @Bean} in {@link
 * com.studydeck.infrastructure.config.BeanConfiguration}.
 */
public final class DeleteAccountService implements DeleteAccountUseCase {

  private final UserAccountRepository userAccountRepository;
  private final AuditEventPort auditPort;

  public DeleteAccountService(
      UserAccountRepository userAccountRepository, AuditEventPort auditPort) {
    this.userAccountRepository = userAccountRepository;
    this.auditPort = auditPort;
  }

  @Override
  public void execute(OwnerId ownerId) {
    // Record audit event BEFORE deletion so the event is not rolled back with the row.
    auditPort.record(ownerId, "account.delete", "user_account", ownerId.toString());
    userAccountRepository.deleteById(ownerId);
  }
}
