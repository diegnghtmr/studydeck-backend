package com.studydeck.domain.service;

import com.studydeck.domain.model.CardId;
import com.studydeck.domain.model.CardScheduleState;
import com.studydeck.domain.model.CardState;
import com.studydeck.domain.model.ReviewRating;
import com.studydeck.domain.model.ReviewResult;
import com.studydeck.domain.model.SchedulerAlgorithm;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * SuperMemo 2 (SM-2) spaced-repetition scheduling engine.
 *
 * <h2>Algorithm Source</h2>
 *
 * <p>Implements the classic SM-2 algorithm as published by Piotr Wozniak (1987) and documented at:
 * https://www.supermemo.com/en/blog/application-of-a-computer-to-improve-the-results-obtained-in
 * -working-with-the-supermemo-method
 *
 * <h2>Key Rules</h2>
 *
 * <ul>
 *   <li>Quality mapping: AGAIN=0, HARD=2, GOOD=3, EASY=5 (6-point scale squeezed to 4 buttons)
 *   <li>Quality &lt; 3 (AGAIN / HARD) → reset: interval=1, reps=0, EF unchanged
 *   <li>First repetition: I(1) = 1 day
 *   <li>Second repetition: I(2) = 6 days
 *   <li>n-th repetition (n≥3): I(n) = I(n-1) * EF
 *   <li>EF update: EF' = EF + (0.1 - (5-q)*(0.08 + (5-q)*0.02))
 *   <li>EF floor: 1.3
 *   <li>AGAIN: interval reset to 1 day, reps reset to 0
 * </ul>
 *
 * <p>For SM-2, stability is re-used to store the ease factor (EF). Difficulty is 10 - EF*4 to map
 * from EF range [1.3, 2.5] → difficulty [0, 4.8], scaled to [0, 10] domain. Initial EF = 2.5.
 *
 * <p>Pure Java — no Spring, no JPA annotations. Deterministic: time is passed in as a parameter.
 */
public final class Sm2Scheduler implements SchedulingEngine {

  private static final double INITIAL_EF = 2.5;
  private static final double MIN_EF = 1.3;
  private static final double MAX_EF = 2.5;

  private static final int FIRST_INTERVAL = 1;
  private static final int SECOND_INTERVAL = 6;

  /** Creates an SM-2 scheduler with default parameters. */
  public Sm2Scheduler() {}

  /**
   * Schedules a card review using SM-2, with an explicit card ID.
   *
   * @param cardId card being reviewed
   * @param current current scheduler state
   * @param rating user's rating
   * @param reviewedAt review timestamp (caller-supplied)
   * @param desiredRetention unused by SM-2 algorithm itself; preserved in the state
   * @return a {@link ReviewResult} with previous and next states
   */
  public ReviewResult schedule(
      CardId cardId,
      CardScheduleState current,
      ReviewRating rating,
      Instant reviewedAt,
      double desiredRetention) {
    Objects.requireNonNull(cardId, "cardId must not be null");
    Objects.requireNonNull(current, "current must not be null");
    Objects.requireNonNull(rating, "rating must not be null");
    Objects.requireNonNull(reviewedAt, "reviewedAt must not be null");

    CardScheduleState next = compute(current, rating, reviewedAt, desiredRetention);
    return new ReviewResult(cardId, rating, reviewedAt, current, next);
  }

  @Override
  public ReviewResult schedule(
      CardScheduleState current, ReviewRating rating, Instant reviewedAt, double desiredRetention) {
    Objects.requireNonNull(current, "current must not be null");
    Objects.requireNonNull(rating, "rating must not be null");
    Objects.requireNonNull(reviewedAt, "reviewedAt must not be null");

    CardScheduleState next = compute(current, rating, reviewedAt, desiredRetention);
    CardId sentinel = new CardId(UUID.fromString("00000000-0000-0000-0000-000000000000"));
    return new ReviewResult(sentinel, rating, reviewedAt, current, next);
  }

  // ---------------------------------------------------------------------------
  // SM-2 core computation
  // ---------------------------------------------------------------------------

  private CardScheduleState compute(
      CardScheduleState current, ReviewRating rating, Instant reviewedAt, double desiredRetention) {

    int q = toQuality(rating);
    double ef = current.stability() > 0 ? current.stability() : INITIAL_EF;
    int currentReps = current.reps();
    int lapses = current.lapses();

    if (q < 3) {
      // Failed recall — reset to beginning but keep EF
      int newLapses = (current.state() == CardState.REVIEW) ? lapses + 1 : lapses;
      int scheduledDays = FIRST_INTERVAL;
      Instant dueAt = reviewedAt.plus(scheduledDays, ChronoUnit.DAYS);
      // stability (ef) stays same, difficulty stays same
      return current.withState(
          CardState.LEARNING,
          ef,
          current.difficulty(),
          0,
          newLapses,
          scheduledDays,
          dueAt,
          reviewedAt);
    }

    // Successful recall — update EF and compute interval
    double newEf = clampEf(ef + (0.1 - (5.0 - q) * (0.08 + (5.0 - q) * 0.02)));
    int newReps = currentReps + 1;
    int scheduledDays = nextInterval(currentReps, ef, current.scheduledDays());
    Instant dueAt = reviewedAt.plus(scheduledDays, ChronoUnit.DAYS);

    // Convert EF back to difficulty (10 - EF*4 clamped 0-10)
    double newDifficulty = clampDifficulty(10.0 - newEf * 4.0);

    // Graduate to REVIEW if in LEARNING/RELEARNING
    CardState nextState =
        (current.state() == CardState.NEW
                || current.state() == CardState.LEARNING
                || current.state() == CardState.RELEARNING)
            ? CardState.REVIEW
            : CardState.REVIEW;

    return new CardScheduleState(
        SchedulerAlgorithm.SM2,
        nextState,
        newEf,
        newDifficulty,
        desiredRetention,
        newReps,
        lapses,
        scheduledDays,
        dueAt,
        reviewedAt);
  }

  /**
   * Classic SM-2 interval progression.
   *
   * <pre>
   * I(1) = 1 day
   * I(2) = 6 days
   * I(n) = I(n-1) * EF   for n >= 3
   * </pre>
   */
  int nextInterval(int currentReps, double ef, int previousInterval) {
    return switch (currentReps) {
      case 0 -> FIRST_INTERVAL;
      case 1 -> SECOND_INTERVAL;
      default -> Math.max(1, (int) Math.round(previousInterval * ef));
    };
  }

  /**
   * Maps a four-button rating to the SM-2 0–5 quality scale.
   *
   * <pre>
   * AGAIN → 0  (complete blackout)
   * HARD  → 2  (correct with serious difficulty)
   * GOOD  → 3  (correct with some effort)
   * EASY  → 5  (perfect recall)
   * </pre>
   */
  static int toQuality(ReviewRating rating) {
    return switch (rating) {
      case AGAIN -> 0;
      case HARD -> 2;
      case GOOD -> 3;
      case EASY -> 5;
    };
  }

  private double clampEf(double ef) {
    return Math.max(MIN_EF, Math.min(MAX_EF, ef));
  }

  private double clampDifficulty(double d) {
    return Math.max(0.0, Math.min(10.0, d));
  }
}
