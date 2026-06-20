package com.studydeck.domain.port.in;

import com.studydeck.domain.model.NoteId;
import com.studydeck.domain.model.OwnerId;

/** Input port — deletes a Note and all its derived Cards. */
public interface DeleteNoteUseCase {

  /**
   * Deletes a Note and its cards.
   *
   * @param command (non-null)
   * @throws com.studydeck.application.exception.NotFoundException if the note is not found or its
   *     deck is not owned by the caller
   */
  void execute(Command command);

  /**
   * Command parameters.
   *
   * @param ownerId authenticated user (non-null)
   * @param noteId id of the Note to delete (non-null)
   */
  record Command(OwnerId ownerId, NoteId noteId) {

    public Command {
      if (ownerId == null) {
        throw new IllegalArgumentException("ownerId must not be null");
      }
      if (noteId == null) {
        throw new IllegalArgumentException("noteId must not be null");
      }
    }
  }
}
