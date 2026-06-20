package com.studydeck.domain.port.in;

import com.studydeck.application.common.Page;
import com.studydeck.application.common.PageRequest;
import com.studydeck.domain.model.Card;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.OwnerId;

/** Input port — queries Cards with filtering and pagination. */
public interface ListCardsQuery {

  /**
   * Lists cards accessible to the caller.
   *
   * @param query (non-null)
   * @return paginated page of cards
   */
  Page<Card> execute(Query query);

  /**
   * Query parameters.
   *
   * @param ownerId authenticated user (non-null)
   * @param deckId optional deck filter; null means all decks owned by the caller
   * @param suspended optional suspended filter; null means all
   * @param pageRequest pagination parameters (non-null)
   */
  record Query(OwnerId ownerId, DeckId deckId, Boolean suspended, PageRequest pageRequest) {

    public Query {
      if (ownerId == null) {
        throw new IllegalArgumentException("ownerId must not be null");
      }
      if (pageRequest == null) {
        throw new IllegalArgumentException("pageRequest must not be null");
      }
    }
  }
}
