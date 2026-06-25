package com.studydeck.domain.port.in;

import com.studydeck.domain.model.OwnerId;
import java.util.Objects;

/**
 * Input port — revoke a specific Identity Provider session.
 *
 * <p>Ownership is enforced: the session MUST belong to the requesting user, otherwise {@link
 * com.studydeck.application.exception.NotFoundException} is thrown.
 *
 * <p>Framework-free: no Spring annotations.
 */
public interface RevokeSessionUseCase {

  void execute(Command command);

  record Command(OwnerId ownerId, String sessionId) {
    public Command {
      Objects.requireNonNull(ownerId, "ownerId must not be null");
      Objects.requireNonNull(sessionId, "sessionId must not be null");
    }
  }
}
