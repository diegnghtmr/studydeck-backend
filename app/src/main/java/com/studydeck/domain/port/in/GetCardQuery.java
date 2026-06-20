package com.studydeck.domain.port.in;

import com.studydeck.domain.model.Card;
import com.studydeck.domain.model.CardId;
import com.studydeck.domain.model.OwnerId;

/** Input port — retrieves a single Card by id, enforcing deck ownership. */
public interface GetCardQuery {

  /**
   * Gets a Card.
   *
   * @param query (non-null)
   * @return the Card
   * @throws com.studydeck.application.exception.NotFoundException if the card is not found or its
   *     deck is not owned by the caller
   */
  Card execute(Query query);

  /**
   * Query parameters.
   *
   * @param ownerId authenticated user (non-null)
   * @param cardId id of the requested Card (non-null)
   */
  record Query(OwnerId ownerId, CardId cardId) {

    public Query {
      if (ownerId == null) {
        throw new IllegalArgumentException("ownerId must not be null");
      }
      if (cardId == null) {
        throw new IllegalArgumentException("cardId must not be null");
      }
    }
  }
}
