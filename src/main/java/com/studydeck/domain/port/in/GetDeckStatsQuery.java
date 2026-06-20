package com.studydeck.domain.port.in;

import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.OwnerId;
import java.util.Objects;

/** Input port — deck statistics (GET /v1/decks/{deckId}/stats). */
public interface GetDeckStatsQuery {

  DeckStatsResult execute(Query query);

  record Query(OwnerId ownerId, DeckId deckId) {
    public Query {
      Objects.requireNonNull(ownerId, "ownerId must not be null");
      Objects.requireNonNull(deckId, "deckId must not be null");
    }
  }

  record DeckStatsResult(
      DeckId deckId,
      int totalNotes,
      int totalCards,
      int dueToday,
      int reviewedToday,
      int suspendedCards,
      Double againRate7d,
      Double averageRetention30d) {}
}
