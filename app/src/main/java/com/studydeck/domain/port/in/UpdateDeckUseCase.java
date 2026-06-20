package com.studydeck.domain.port.in;

import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.OwnerId;
import java.util.List;

/** Input port — updates title, description, tags, and retention of an existing Deck. */
public interface UpdateDeckUseCase {

  /**
   * Updates a Deck.
   *
   * @param command the update parameters (non-null)
   * @throws com.studydeck.application.exception.NotFoundException if the deck does not exist or is
   *     not owned by the caller
   */
  void execute(Command command);

  /**
   * Self-validating command record.
   *
   * @param ownerId authenticated user (non-null)
   * @param deckId id of the Deck to update (non-null)
   * @param title new title; non-blank, max 120 chars
   * @param description new description; may be null; max 1000 chars
   * @param tags new tag list; may be null
   * @param defaultDesiredRetention 0.70–0.99
   */
  record Command(
      OwnerId ownerId,
      DeckId deckId,
      String title,
      String description,
      List<String> tags,
      double defaultDesiredRetention) {

    public Command {
      if (ownerId == null) {
        throw new IllegalArgumentException("ownerId must not be null");
      }
      if (deckId == null) {
        throw new IllegalArgumentException("deckId must not be null");
      }
      if (title == null || title.isBlank()) {
        throw new IllegalArgumentException("title must not be blank");
      }
      if (title.length() > 120) {
        throw new IllegalArgumentException("title exceeds 120 character limit");
      }
      if (description != null && description.length() > 1000) {
        throw new IllegalArgumentException("description exceeds 1000 character limit");
      }
      if (defaultDesiredRetention < 0.70 || defaultDesiredRetention > 0.99) {
        throw new IllegalArgumentException(
            "defaultDesiredRetention must be between 0.70 and 0.99 inclusive");
      }
    }

    /** Convenience constructor using default retention (0.9) and no tags. */
    public Command(OwnerId ownerId, DeckId deckId, String title, String description) {
      this(ownerId, deckId, title, description, null, 0.9);
    }
  }
}
