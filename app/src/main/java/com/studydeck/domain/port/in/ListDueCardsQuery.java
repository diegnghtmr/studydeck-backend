package com.studydeck.domain.port.in;

import com.studydeck.domain.model.Card;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.OwnerId;
import java.util.List;
import java.util.Objects;

/** Input port — list cards due for review (GET /v1/cards/due). */
public interface ListDueCardsQuery {

  List<Card> execute(Query query);

  record Query(OwnerId ownerId, DeckId deckId, int limit) {
    public Query {
      Objects.requireNonNull(ownerId, "ownerId must not be null");
      if (limit < 1 || limit > 200) {
        throw new IllegalArgumentException("limit must be in [1, 200], got " + limit);
      }
    }
  }
}
