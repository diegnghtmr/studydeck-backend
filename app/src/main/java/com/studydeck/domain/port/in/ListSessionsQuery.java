package com.studydeck.domain.port.in;

import com.studydeck.domain.model.IdpSession;
import com.studydeck.domain.model.OwnerId;
import java.util.List;
import java.util.Objects;

/**
 * Input port — list all active Identity Provider sessions for the authenticated user.
 *
 * <p>Framework-free: no Spring annotations.
 */
public interface ListSessionsQuery {

  List<IdpSession> execute(Query query);

  record Query(OwnerId ownerId) {
    public Query {
      Objects.requireNonNull(ownerId, "ownerId must not be null");
    }
  }
}
