package com.studydeck.application.service;

import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.in.DeleteAccountUseCase;
import com.studydeck.domain.port.out.AuditEventPort;
import com.studydeck.domain.port.out.IdpAdminPort;
import com.studydeck.domain.port.out.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application service implementing GDPR right to erasure.
 *
 * <p>Deletes the {@code user_account} row first; the database cascade (ON DELETE CASCADE) removes
 * all child rows automatically. The audit event is recorded AFTER a successful delete so that a
 * failed delete never produces a false "account deleted" audit trail.
 *
 * <p>After app-data deletion, attempts to delete the user from the Identity Provider (best-effort).
 * If the IdP deletion fails, a warning is logged but the operation is considered successful — app
 * data deletion takes priority over IdP cleanup.
 *
 * <p>Framework-free: no Spring annotations. Wired as {@code @Bean} in {@link
 * com.studydeck.infrastructure.config.BeanConfiguration}.
 */
public final class DeleteAccountService implements DeleteAccountUseCase {

  private static final Logger log = LoggerFactory.getLogger(DeleteAccountService.class);

  private final UserAccountRepository userAccountRepository;
  private final AuditEventPort auditPort;
  private final IdpAdminPort idpAdminPort;

  public DeleteAccountService(
      UserAccountRepository userAccountRepository,
      AuditEventPort auditPort,
      IdpAdminPort idpAdminPort) {
    this.userAccountRepository = userAccountRepository;
    this.auditPort = auditPort;
    this.idpAdminPort = idpAdminPort;
  }

  @Override
  public void execute(OwnerId ownerId) {
    // 1. Delete app data first. On failure (exception) the audit event is NOT written → no false
    // audit trail.
    userAccountRepository.deleteById(ownerId);
    // 2. Record audit only after successful app-data deletion.
    auditPort.record(ownerId, "account.delete", "user_account", ownerId.toString());
    // 3. Best-effort IdP user deletion. Never let IdP failures roll back completed app-data
    // deletion.
    try {
      idpAdminPort.deleteUser(ownerId.value().toString());
    } catch (Exception e) {
      log.warn(
          "App data deleted for ownerId={} but IdP user deletion failed (best-effort): {}",
          ownerId,
          e.getMessage());
    }
  }
}
