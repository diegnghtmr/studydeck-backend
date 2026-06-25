package com.studydeck.domain.port.in;

import com.studydeck.domain.model.CardId;
import com.studydeck.domain.model.OwnerId;
import java.util.Objects;

/** Input port — computes preview scheduling intervals for all 4 ratings without persisting. */
public interface GetPreviewIntervalsQuery {

  /**
   * Computes the hypothetical next interval (in days) for each rating given the card's current
   * schedule state and the user's algorithm preference.
   *
   * @param query non-null
   * @return preview intervals for all four ratings
   */
  PreviewIntervals execute(Query query);

  record Query(OwnerId ownerId, CardId cardId) {
    public Query {
      Objects.requireNonNull(ownerId, "ownerId must not be null");
      Objects.requireNonNull(cardId, "cardId must not be null");
    }
  }

  record PreviewIntervals(int again, int hard, int good, int easy) {}
}
