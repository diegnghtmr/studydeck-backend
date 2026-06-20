package com.studydeck.domain.port.in;

import com.studydeck.domain.model.Card;
import com.studydeck.domain.model.NoteId;
import com.studydeck.domain.model.OwnerId;
import java.util.List;

/** Input port — lists all Cards derived from a given Note, enforcing deck ownership. */
public interface ListCardsForNoteQuery {

  /**
   * Lists cards for the given note.
   *
   * @param query (non-null)
   * @return ordered list of cards (by ordinal asc)
   * @throws com.studydeck.application.exception.NotFoundException if the note is not found or its
   *     deck is not owned by the caller
   */
  List<Card> execute(Query query);

  /**
   * Query parameters.
   *
   * @param ownerId authenticated user (non-null)
   * @param noteId id of the Note (non-null)
   */
  record Query(OwnerId ownerId, NoteId noteId) {

    public Query {
      if (ownerId == null) {
        throw new IllegalArgumentException("ownerId must not be null");
      }
      if (noteId == null) {
        throw new IllegalArgumentException("noteId must not be null");
      }
    }
  }
}
