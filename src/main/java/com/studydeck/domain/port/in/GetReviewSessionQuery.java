package com.studydeck.domain.port.in;

import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.out.ReviewSessionRepository.ReviewSessionView;
import java.util.Objects;
import java.util.UUID;

/** Input port — get an existing review session by ID. */
public interface GetReviewSessionQuery {

  ReviewSessionView execute(Query query);

  record Query(OwnerId ownerId, UUID sessionId) {
    public Query {
      Objects.requireNonNull(ownerId, "ownerId must not be null");
      Objects.requireNonNull(sessionId, "sessionId must not be null");
    }
  }
}
