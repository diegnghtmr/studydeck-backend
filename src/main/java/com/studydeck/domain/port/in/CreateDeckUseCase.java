package com.studydeck.domain.port.in;

import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.OwnerId;
import java.util.List;

/** Input port — creates a new Deck owned by the given user. */
public interface CreateDeckUseCase {

  /**
   * Creates a Deck.
   *
   * @param command the creation parameters (non-null)
   * @return the id of the newly created Deck
   */
  DeckId execute(Command command);

  /**
   * Self-validating command record.
   *
   * @param ownerId the authenticated user (non-null)
   * @param title non-blank, max 120 chars
   * @param description optional; may be null; max 1000 chars
   * @param tags optional tag list; may be null
   * @param defaultDesiredRetention 0.70–0.99; defaults to 0.9
   */
  record Command(
      OwnerId ownerId,
      String title,
      String description,
      List<String> tags,
      double defaultDesiredRetention) {

    public Command {
      if (ownerId == null) {
        throw new IllegalArgumentException("ownerId must not be null");
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
    public Command(OwnerId ownerId, String title, String description) {
      this(ownerId, title, description, null, 0.9);
    }
  }
}
