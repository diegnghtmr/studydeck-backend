package com.studydeck.domain.port.in;

import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.OwnerId;

/** Input port — restores an archived Deck (unarchive / soft-delete undo). Idempotent. */
public interface UnarchiveDeckUseCase {

  /**
   * Unarchives a Deck.
   *
   * @param command (non-null)
   * @throws com.studydeck.application.exception.NotFoundException if the deck does not exist or is
   *     not owned by the caller
   */
  void execute(Command command);

  /**
   * Command parameters.
   *
   * @param ownerId authenticated user (non-null)
   * @param deckId id of the Deck to unarchive (non-null)
   */
  record Command(OwnerId ownerId, DeckId deckId) {

    public Command {
      if (ownerId == null) {
        throw new IllegalArgumentException("ownerId must not be null");
      }
      if (deckId == null) {
        throw new IllegalArgumentException("deckId must not be null");
      }
    }
  }
}
