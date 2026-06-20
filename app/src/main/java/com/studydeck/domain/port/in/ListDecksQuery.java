package com.studydeck.domain.port.in;

import com.studydeck.application.common.Page;
import com.studydeck.application.common.PageRequest;
import com.studydeck.domain.model.Deck;
import com.studydeck.domain.model.OwnerId;

/** Input port — queries the paginated list of Decks for the authenticated user. */
public interface ListDecksQuery {

  /**
   * Lists decks owned by the caller.
   *
   * @param query the query parameters (non-null)
   * @return paginated page of decks
   */
  Page<Deck> execute(Query query);

  /**
   * Query parameters.
   *
   * @param ownerId authenticated user (non-null)
   * @param includeArchived when false, archived decks are excluded
   * @param search optional name/description substring filter; null or blank means no filter
   * @param pageRequest pagination parameters (non-null)
   */
  record Query(OwnerId ownerId, boolean includeArchived, String search, PageRequest pageRequest) {

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
