package com.studydeck.domain.port.in;

import com.studydeck.domain.model.CardId;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.ReviewRating;
import com.studydeck.domain.model.ReviewResult;
import java.util.Objects;
import java.util.UUID;

/** Input port — submit a review rating for a card. */
public interface SubmitReviewUseCase {

  /**
   * Processes a review submission.
   *
   * @return the ReviewResult carrying previousState, nextState, and log entry id
   */
  Result execute(Command command);

  record Command(
      OwnerId ownerId, CardId cardId, ReviewRating rating, UUID sessionId, Integer responseTimeMs) {
    public Command {
      Objects.requireNonNull(ownerId, "ownerId must not be null");
      Objects.requireNonNull(cardId, "cardId must not be null");
      Objects.requireNonNull(rating, "rating must not be null");
      if (responseTimeMs != null && responseTimeMs < 0) {
        throw new IllegalArgumentException("responseTimeMs must be >= 0, got " + responseTimeMs);
      }
    }
  }

  record Result(ReviewResult reviewResult, UUID historyEntryId) {}
}
