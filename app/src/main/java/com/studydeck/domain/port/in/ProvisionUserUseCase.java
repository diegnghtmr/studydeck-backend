package com.studydeck.domain.port.in;

import com.studydeck.domain.model.OwnerId;

/**
 * Input port — ensures a {@code user_account} row exists for an authenticated JWT principal.
 *
 * <p>Idempotent: calling with the same {@link Command#userId()} twice must not create duplicate
 * rows. On subsequent calls the account is left unchanged (no unnecessary writes on the hot path).
 *
 * <p>Called by the JIT provisioning filter in the infrastructure layer on every authenticated
 * request before any controller handler runs.
 */
public interface ProvisionUserUseCase {

  /**
   * Ensures the user account exists. Creates it on first call; no-ops on subsequent calls for the
   * same subject.
   *
   * @param command non-null
   */
  void execute(Command command);

  /**
   * Provisioning command.
   *
   * @param userId the JWT {@code sub} claim as an {@link OwnerId} (non-null)
   * @param email the JWT {@code email} claim (non-blank)
   * @param displayName the JWT {@code name} or {@code preferred_username} claim (may be null)
   */
  record Command(OwnerId userId, String email, String displayName) {

    public Command {
      if (userId == null) {
        throw new IllegalArgumentException("userId must not be null");
      }
      if (email == null || email.isBlank()) {
        throw new IllegalArgumentException("email must not be blank");
      }
    }
  }
}
