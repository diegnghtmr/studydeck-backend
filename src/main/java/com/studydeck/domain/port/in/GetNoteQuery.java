package com.studydeck.domain.port.in;

import com.studydeck.domain.model.Note;
import com.studydeck.domain.model.NoteId;
import com.studydeck.domain.model.OwnerId;

/** Input port — retrieves a single Note by id, enforcing deck ownership. */
public interface GetNoteQuery {

  /**
   * Gets a Note.
   *
   * @param query (non-null)
   * @return the Note
   * @throws com.studydeck.application.exception.NotFoundException if the note is not found or its
   *     deck is not owned by the caller
   */
  Note execute(Query query);

  /**
   * Query parameters.
   *
   * @param ownerId authenticated user (non-null)
   * @param noteId id of the requested Note (non-null)
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
