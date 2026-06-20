package com.studydeck.domain.port.in;

import com.studydeck.domain.model.CardId;
import com.studydeck.domain.model.NoteContent;
import com.studydeck.domain.model.NoteId;
import com.studydeck.domain.model.OwnerId;
import java.util.List;

/**
 * Input port — updates a Note's content and/or tags; re-generates cards when content changes; bumps
 * the version.
 */
public interface UpdateNoteUseCase {

  /**
   * Updates a Note.
   *
   * @param command (non-null)
   * @return result with the new card ids (may differ from original if content changed type)
   * @throws com.studydeck.application.exception.NotFoundException if the note is not found or its
   *     deck is not owned by the caller
   */
  Result execute(Command command);

  /**
   * Command parameters.
   *
   * @param ownerId authenticated user (non-null)
   * @param noteId id of the Note to update (non-null)
   * @param content new typed note content (non-null)
   * @param tags new tag list; null treated as empty list
   */
  record Command(OwnerId ownerId, NoteId noteId, NoteContent content, List<String> tags) {

    public Command {
      if (ownerId == null) {
        throw new IllegalArgumentException("ownerId must not be null");
      }
      if (noteId == null) {
        throw new IllegalArgumentException("noteId must not be null");
      }
      if (content == null) {
        throw new IllegalArgumentException("content must not be null");
      }
    }
  }

  /**
   * Result of note update.
   *
   * @param noteId the updated note's id
   * @param cardIds ordered list of new card ids (old cards deleted, new ones generated)
   * @param version the note's new version number
   */
  record Result(NoteId noteId, List<CardId> cardIds, int version) {}
}
