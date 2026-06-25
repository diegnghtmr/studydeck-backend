package com.studydeck.domain.port.in;

import com.studydeck.domain.model.OwnerId;

/**
 * Use case: revoke all active Identity Provider sessions for the authenticated user.
 *
 * <p>Framework-free: no Spring annotations.
 */
public interface LogoutAllSessionsUseCase {

  /** Revokes all sessions for the given owner. */
  void execute(OwnerId ownerId);
}
