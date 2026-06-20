package com.studydeck.domain.port.in;

import com.studydeck.domain.model.CardId;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.NoteContent;
import com.studydeck.domain.model.NoteId;
import com.studydeck.domain.model.OwnerId;
import java.util.List;

/**
 * Input port — creates a Note, runs CardGenerator, and persists note + generated cards atomically.
 */
public interface CreateNoteUseCase {

  /**
   * Creates a Note and its derived cards.
   *
   * @param command (non-null)
   * @return result containing the note id and generated card ids
   * @throws com.studydeck.application.exception.NotFoundException if the deck is not found or not
   *     owned by the caller
   */
  Result execute(Command command);

  /**
   * Command parameters.
   *
   * @param ownerId authenticated user (non-null)
   * @param deckId target deck (non-null)
   * @param content typed note content (non-null)
   * @param tags optional tags; null treated as empty list
   */
  record Command(OwnerId ownerId, DeckId deckId, NoteContent content, List<String> tags) {

    public Command {
      if (ownerId == null) {
        throw new IllegalArgumentException("ownerId must not be null");
      }
      if (deckId == null) {
        throw new IllegalArgumentException("deckId must not be null");
      }
      if (content == null) {
        throw new IllegalArgumentException("content must not be null");
      }
    }
  }

  /**
   * Result of note creation.
   *
   * @param noteId the created note's id
   * @param cardIds ordered list of generated card ids
   */
  record Result(NoteId noteId, List<CardId> cardIds) {}
}
