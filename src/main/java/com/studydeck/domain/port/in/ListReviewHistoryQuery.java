package com.studydeck.domain.port.in;

import com.studydeck.application.common.Page;
import com.studydeck.application.common.PageRequest;
import com.studydeck.domain.model.CardId;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.ReviewLog;
import java.time.Instant;
import java.util.Objects;

/** Input port — paginated review history (GET /v1/reviews/history). */
public interface ListReviewHistoryQuery {

  Page<ReviewLog> execute(Query query);

  record Query(
      OwnerId ownerId,
      DeckId deckId,
      CardId cardId,
      Instant from,
      Instant to,
      PageRequest pageRequest) {
    public Query {
      Objects.requireNonNull(ownerId, "ownerId must not be null");
      Objects.requireNonNull(pageRequest, "pageRequest must not be null");
    }
  }
}
