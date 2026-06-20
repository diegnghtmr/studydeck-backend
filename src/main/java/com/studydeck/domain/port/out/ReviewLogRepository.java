package com.studydeck.domain.port.out;

import com.studydeck.domain.model.CardId;
import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.ReviewLog;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Output port — append-only persistence contract for ReviewLog entries.
 *
 * <p>Review logs are immutable once written. The generated ID is returned so callers can include it
 * in responses.
 */
public interface ReviewLogRepository {

  /**
   * Appends a new review log entry.
   *
   * @param ownerId the reviewing user
   * @param sessionId optional session ID (may be null)
   * @param log the review log to persist
   * @return the generated UUID for the persisted entry
   */
  UUID save(OwnerId ownerId, UUID sessionId, ReviewLog log);

  /**
   * Loads paginated review history for an owner, optionally filtered by deck, card, and date range.
   *
   * @param ownerId the reviewing user
   * @param deckId optional deck filter
   * @param cardId optional card filter
   * @param from optional start of date range (inclusive)
   * @param to optional end of date range (inclusive)
   * @param offset page offset
   * @param limit page size
   */
  List<ReviewLog> findHistory(
      OwnerId ownerId,
      DeckId deckId,
      CardId cardId,
      Instant from,
      Instant to,
      int offset,
      int limit);

  /** Counts review log entries matching the same filters. */
  long countHistory(OwnerId ownerId, DeckId deckId, CardId cardId, Instant from, Instant to);

  /**
   * Counts distinct reviews performed today for a deck (for DeckStats.reviewedToday).
   *
   * @param ownerId the reviewing user
   * @param deckId the deck
   * @param dayStart start of today (UTC midnight)
   * @param dayEnd start of tomorrow (UTC midnight)
   */
  int countReviewedToday(OwnerId ownerId, DeckId deckId, Instant dayStart, Instant dayEnd);

  /**
   * Computes the "again" rate over the last 7 days for a deck.
   *
   * @return fraction of reviews rated AGAIN in [0, 1], or null when no reviews exist
   */
  Double againRate7d(OwnerId ownerId, DeckId deckId, Instant since);

  /**
   * Computes average retention (non-AGAIN fraction) over the last 30 days for a deck.
   *
   * @return average retention in [0, 1], or null when no reviews exist
   */
  Double averageRetention30d(OwnerId ownerId, DeckId deckId, Instant since);
}
