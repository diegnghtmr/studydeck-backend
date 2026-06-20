package com.studydeck.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Value object representing the full scheduler state for one card.
 *
 * <p>This is the domain mirror of the OpenAPI {@code SchedulerState} schema. It carries the
 * algorithm-agnostic state fields plus the algorithm identifier so the right engine can be selected
 * on the next review.
 *
 * <p>Invariants:
 *
 * <ul>
 *   <li>{@code algorithm} — non-null
 *   <li>{@code state} — non-null
 *   <li>{@code stability} — ≥ 0.0
 *   <li>{@code difficulty} — 0.0–10.0 (FSRS) or 1.3–2.5 ease factor (SM-2)
 *   <li>{@code desiredRetention} — 0.70–0.99
 *   <li>{@code reps} — ≥ 0
 *   <li>{@code lapses} — ≥ 0
 *   <li>{@code scheduledDays} — ≥ 0
 *   <li>{@code dueAt} — non-null
 * </ul>
 *
 * <p>Pure Java — no Spring, no JPA annotations.
 */
public record CardScheduleState(
    SchedulerAlgorithm algorithm,
    CardState state,
    double stability,
    double difficulty,
    double desiredRetention,
    int reps,
    int lapses,
    int scheduledDays,
    Instant dueAt,
    Instant lastReviewedAt) {

  public CardScheduleState {
    Objects.requireNonNull(algorithm, "algorithm must not be null");
    Objects.requireNonNull(state, "state must not be null");
    Objects.requireNonNull(dueAt, "dueAt must not be null");
    if (stability < 0.0) {
      throw new IllegalArgumentException("stability must be >= 0, got " + stability);
    }
    if (difficulty < 0.0 || difficulty > 10.0) {
      throw new IllegalArgumentException("difficulty must be in [0, 10], got " + difficulty);
    }
    if (desiredRetention < 0.70 || desiredRetention > 0.99) {
      throw new IllegalArgumentException(
          "desiredRetention must be in [0.70, 0.99], got " + desiredRetention);
    }
    if (reps < 0) {
      throw new IllegalArgumentException("reps must be >= 0, got " + reps);
    }
    if (lapses < 0) {
      throw new IllegalArgumentException("lapses must be >= 0, got " + lapses);
    }
    if (scheduledDays < 0) {
      throw new IllegalArgumentException("scheduledDays must be >= 0, got " + scheduledDays);
    }
  }

  /**
   * Creates the initial schedule state for a brand-new card.
   *
   * @param algorithm algorithm to use
   * @param desiredRetention desired retention ratio (0.70–0.99)
   * @param now current timestamp (used as dueAt for a new card)
   */
  public static CardScheduleState newCard(
      SchedulerAlgorithm algorithm, double desiredRetention, Instant now) {
    Objects.requireNonNull(now, "now must not be null");
    return new CardScheduleState(
        algorithm, CardState.NEW, 0.0, 5.0, desiredRetention, 0, 0, 0, now, null);
  }

  /**
   * Convenience factory — FSRS new card with default desired retention (0.9).
   *
   * @param now current timestamp
   */
  public static CardScheduleState newFsrsCard(Instant now) {
    return newCard(SchedulerAlgorithm.FSRS, 0.9, now);
  }

  /**
   * Returns a copy of this state with the given fields updated. Keeps all other fields unchanged.
   */
  public CardScheduleState withState(
      CardState newState,
      double newStability,
      double newDifficulty,
      int newReps,
      int newLapses,
      int newScheduledDays,
      Instant newDueAt,
      Instant newLastReviewedAt) {
    return new CardScheduleState(
        this.algorithm,
        newState,
        newStability,
        newDifficulty,
        this.desiredRetention,
        newReps,
        newLapses,
        newScheduledDays,
        newDueAt,
        newLastReviewedAt);
  }
}
