package com.studydeck.domain.port.in;

import com.studydeck.domain.model.Deck;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.OwnerId;

/** Input port — retrieves a single Deck by id, enforcing ownership. */
public interface GetDeckQuery {

  /**
   * Gets a Deck.
   *
   * @param query (non-null)
   * @return the Deck
   * @throws com.studydeck.application.exception.NotFoundException if not found or not owned by the
   *     caller
   */
  Deck execute(Query query);

  /**
   * Query parameters.
   *
   * @param ownerId authenticated user (non-null)
   * @param deckId id of the requested Deck (non-null)
   */
  record Query(OwnerId ownerId, DeckId deckId) {

    public Query {
      if (ownerId == null) {
        throw new IllegalArgumentException("ownerId must not be null");
      }
      if (deckId == null) {
        throw new IllegalArgumentException("deckId must not be null");
      }
    }
  }
}
