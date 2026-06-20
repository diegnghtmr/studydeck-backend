package com.studydeck.domain.port.in;

import com.studydeck.domain.model.Card;
import com.studydeck.domain.model.OwnerId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Input port — get the next due card for a session (204 when none remaining). */
public interface GetNextCardQuery {

  /**
   * Returns the next due card for the session, or empty when no cards remain.
   *
   * <p>An empty result maps to HTTP 204.
   */
  Optional<Card> execute(Query query);

  record Query(OwnerId ownerId, UUID sessionId) {
    public Query {
      Objects.requireNonNull(ownerId, "ownerId must not be null");
      Objects.requireNonNull(sessionId, "sessionId must not be null");
    }
  }
}
