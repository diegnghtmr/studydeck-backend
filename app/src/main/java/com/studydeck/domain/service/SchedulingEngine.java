package com.studydeck.domain.service;

import com.studydeck.domain.model.CardScheduleState;
import com.studydeck.domain.model.ReviewRating;
import com.studydeck.domain.model.ReviewResult;
import java.time.Instant;

/**
 * Port (domain service interface) for spaced-repetition scheduling.
 *
 * <p>Implementations must be pure and deterministic: given the same inputs they must always produce
 * the same output. Time is passed in as a parameter; implementations must never call {@code
 * Instant.now()} or any clock internally.
 *
 * <p>Known implementations:
 *
 * <ul>
 *   <li>{@link FsrsScheduler} — FSRS v5.5 (default)
 *   <li>{@link Sm2Scheduler} — SuperMemo 2 (legacy compatibility)
 * </ul>
 *
 * <p>The AI subsystem must NEVER call this port directly. Review actions come exclusively from
 * explicit user input routed through application-layer use cases.
 */
public interface SchedulingEngine {

  /**
   * Computes the next schedule state for a card after a review.
   *
   * @param current the card's current scheduler state (before this review)
   * @param rating the user's rating
   * @param reviewedAt the exact moment the review occurred (caller-supplied — never internal clock)
   * @param desiredRetention target retention ratio (0.70–0.99)
   * @return a {@link ReviewResult} carrying both the previous and next state
   */
  ReviewResult schedule(
      CardScheduleState current, ReviewRating rating, Instant reviewedAt, double desiredRetention);
}
