package com.studydeck.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain value object carrying the before/after scheduling states for one review event.
 *
 * <p>Mirrors the OpenAPI {@code FSRSReviewResult} schema. Produced by {@link
 * com.studydeck.domain.service.SchedulingEngine} and consumed by the application layer to update
 * the card, persist the review log, and build the HTTP response.
 *
 * <p>Pure Java — no Spring, no JPA annotations.
 */
public record ReviewResult(
    CardId cardId,
    ReviewRating rating,
    Instant reviewedAt,
    CardScheduleState previousState,
    CardScheduleState nextState) {

  public ReviewResult {
    Objects.requireNonNull(cardId, "cardId must not be null");
    Objects.requireNonNull(rating, "rating must not be null");
    Objects.requireNonNull(reviewedAt, "reviewedAt must not be null");
    Objects.requireNonNull(previousState, "previousState must not be null");
    Objects.requireNonNull(nextState, "nextState must not be null");
  }

  /**
   * Builds the {@link ReviewLog} entry from this result.
   *
   * @param elapsedDays days elapsed since the card was last reviewed
   */
  public ReviewLog toReviewLog(int elapsedDays) {
    return new ReviewLog(
        cardId,
        rating,
        previousState.state(),
        reviewedAt,
        elapsedDays,
        nextState.scheduledDays(),
        null);
  }

  /**
   * Builds the {@link ReviewLog} entry including the user's response time.
   *
   * @param elapsedDays days elapsed since the card was last reviewed
   * @param responseTimeMs how long the user took to respond (milliseconds)
   */
  public ReviewLog toReviewLog(int elapsedDays, int responseTimeMs) {
    return new ReviewLog(
        cardId,
        rating,
        previousState.state(),
        reviewedAt,
        elapsedDays,
        nextState.scheduledDays(),
        responseTimeMs);
  }
}
