package com.studydeck.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable record of a single card review event.
 *
 * <p>Mirrors the OpenAPI {@code ReviewLog} schema. The domain variant stores the raw scheduler
 * snapshot ({@link ReviewResult}) rather than a full serialised snapshot so that it remains
 * framework-free and easy to map in persistence adapters.
 *
 * <p>Invariants:
 *
 * <ul>
 *   <li>{@code cardId}, {@code rating}, {@code reviewedAt}, {@code stateBefore} — non-null
 *   <li>{@code elapsedDays} — ≥ 0
 *   <li>{@code scheduledDays} — ≥ 0
 *   <li>{@code responseTimeMs} — ≥ 0 when present
 * </ul>
 *
 * <p>Pure Java — no Spring, no JPA annotations.
 */
public record ReviewLog(
    CardId cardId,
    ReviewRating rating,
    CardState stateBefore,
    Instant reviewedAt,
    int elapsedDays,
    int scheduledDays,
    Integer responseTimeMs) {

  public ReviewLog {
    Objects.requireNonNull(cardId, "cardId must not be null");
    Objects.requireNonNull(rating, "rating must not be null");
    Objects.requireNonNull(stateBefore, "stateBefore must not be null");
    Objects.requireNonNull(reviewedAt, "reviewedAt must not be null");
    if (elapsedDays < 0) {
      throw new IllegalArgumentException("elapsedDays must be >= 0, got " + elapsedDays);
    }
    if (scheduledDays < 0) {
      throw new IllegalArgumentException("scheduledDays must be >= 0, got " + scheduledDays);
    }
    if (responseTimeMs != null && responseTimeMs < 0) {
      throw new IllegalArgumentException(
          "responseTimeMs must be >= 0 when present, got " + responseTimeMs);
    }
  }

  /**
   * Convenience constructor without response time.
   *
   * @param cardId the card being reviewed
   * @param rating the user's rating
   * @param stateBefore card state before this review
   * @param reviewedAt when the review occurred
   * @param elapsedDays days elapsed since last review (0 for new cards)
   * @param scheduledDays days scheduled until next review
   */
  public static ReviewLog of(
      CardId cardId,
      ReviewRating rating,
      CardState stateBefore,
      Instant reviewedAt,
      int elapsedDays,
      int scheduledDays) {
    return new ReviewLog(cardId, rating, stateBefore, reviewedAt, elapsedDays, scheduledDays, null);
  }
}
