package com.studydeck.domain.port.in;

import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.OwnerId;
import java.util.Objects;
import java.util.UUID;

/** Input port — start a new review session. */
public interface StartReviewSessionUseCase {

  UUID execute(Command command);

  record Command(OwnerId ownerId, DeckId deckId, int maxCards) {
    public Command {
      Objects.requireNonNull(ownerId, "ownerId must not be null");
      if (maxCards < 1 || maxCards > 500) {
        throw new IllegalArgumentException("maxCards must be in [1, 500], got " + maxCards);
      }
    }
  }
}
