package com.studydeck.domain.port.in;

import com.studydeck.application.common.Page;
import com.studydeck.application.common.PageRequest;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.Note;
import com.studydeck.domain.model.NoteType;
import com.studydeck.domain.model.OwnerId;

/** Input port — queries Notes with filtering and pagination. */
public interface ListNotesQuery {

  /**
   * Lists notes accessible to the caller.
   *
   * @param query (non-null)
   * @return paginated page of notes
   */
  Page<Note> execute(Query query);

  /**
   * Query parameters.
   *
   * @param ownerId authenticated user (non-null)
   * @param deckId optional deck filter; null means all decks owned by the caller
   * @param noteType optional type filter; null means all types
   * @param tag optional tag filter; null means no tag filter
   * @param search optional text search; null or blank means no filter
   * @param pageRequest pagination parameters (non-null)
   */
  record Query(
      OwnerId ownerId,
      DeckId deckId,
      NoteType noteType,
      String tag,
      String search,
      PageRequest pageRequest) {

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
