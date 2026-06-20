package com.studydeck.domain.port.out;

import com.studydeck.domain.model.DeckId;
import com.studydeck.domain.model.OwnerId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Output port — persistence contract for ReviewSession.
 *
 * <p>A review session is a lightweight envelope tracking when a study session started and its
 * current status. It does not enumerate cards — card selection is driven by
 * CardScheduleStateRepository.
 */
public interface ReviewSessionRepository {

  /**
   * Persists a new review session and returns its generated ID.
   *
   * @param ownerId the session owner
   * @param deckId optional deck restriction (null = all decks)
   * @param maxCards maximum cards to include
   * @param startedAt session start timestamp
   * @return the new session UUID
   */
  UUID create(OwnerId ownerId, DeckId deckId, int maxCards, Instant startedAt);

  /** Loads a review session by its ID. Returns empty when not found. */
  Optional<ReviewSessionView> findById(UUID sessionId);

  /** Increments presentedCount for a session. */
  void incrementPresentedCount(UUID sessionId);

  /** Increments answeredCount for a session. */
  void incrementAnsweredCount(UUID sessionId);

  /** Marks a session as completed. */
  void complete(UUID sessionId, Instant endedAt);

  /**
   * Read-only projection of a review session.
   *
   * @param id session UUID
   * @param ownerId owner of the session
   * @param deckId optional deck (null = all decks)
   * @param maxCards maximum cards configured for this session
   * @param status current session status string
   * @param startedAt when the session was created
   * @param endedAt when the session ended (null if still active)
   * @param presentedCount how many cards were shown
   * @param answeredCount how many cards were answered
   */
  record ReviewSessionView(
      UUID id,
      OwnerId ownerId,
      DeckId deckId,
      int maxCards,
      String status,
      Instant startedAt,
      Instant endedAt,
      int presentedCount,
      int answeredCount) {}
}
