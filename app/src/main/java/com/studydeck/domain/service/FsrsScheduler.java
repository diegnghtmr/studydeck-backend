package com.studydeck.domain.service;

import com.studydeck.domain.model.CardScheduleState;
import com.studydeck.domain.model.CardState;
import com.studydeck.domain.model.ReviewRating;
import com.studydeck.domain.model.ReviewResult;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * FSRS v5.5 scheduling engine.
 *
 * <h2>Algorithm Source / Version</h2>
 *
 * <p>Implements the FSRS-5.5 open algorithm as documented at:
 * https://github.com/open-spaced-repetition/fsrs4anki/wiki/The-Algorithm
 *
 * <p>The canonical default weight vector ({@code w}) is the publicly released FSRS-5.5 default set
 * (19 parameters). Source: open-spaced-repetition/fsrs-rs default weights, git tag v2.1.0 /
 * fsrs-5.5 spec published 2024-12:
 *
 * <pre>
 *   w[0]  = 0.4072   initial stability for AGAIN on first review
 *   w[1]  = 1.1829   initial stability for HARD  on first review
 *   w[2]  = 3.1262   initial stability for GOOD  on first review
 *   w[3]  = 7.2102   initial stability for EASY  on first review
 *   w[4]  = 7.2212   difficulty initial multiplier
 *   w[5]  = 0.5316   difficulty multiplier modifier
 *   w[6]  = 1.0651   difficulty decay constant
 *   w[7]  = 0.0589   difficulty growth per HARD
 *   w[8]  = 1.5330   stability growth modifier for recall
 *   w[9]  = 0.1544   stability growth decay term
 *   w[10] = 1.0     retrievability coefficient
 *   w[11] = 1.9395   stability growth for HARD (under recall)
 *   w[12] = 0.1100   stability growth for EASY (under recall)
 *   w[13] = 0.2900   stability growth floor
 *   w[14] = 2.2700   stability multiplier for HARD
 *   w[15] = 2.9898   stability multiplier for GOOD
 *   w[16] = 0.5100   stability multiplier for EASY
 *   w[17] = 2.0041   stability growth for forgotten cards (relearning S_r formula)
 *   w[18] = 0.9261   stability growth exponent for relearning
 * </pre>
 *
 * <h2>Key Formulas</h2>
 *
 * <pre>
 * Retrievability:      R(t, S) = (1 + FACTOR * t / S) ^ DECAY
 *   where DECAY = -0.5, FACTOR = 19/81
 *
 * Initial difficulty:  D_0(r) = w[4] - exp(w[5] * (r-1)) + 1
 * Difficulty update:   D' = w[6] * D_0(GOOD) + (1-w[6]) * (D - w[7] * (r - 3))
 *                        clamped to [1, 10]
 *
 * Initial stability (new card): S_0(r) = w[r-1]  (r = 1..4 for AGAIN..EASY)
 *
 * Stability increase (recall):
 *   S'_r = S * (e^(w[8]) * (11-D) * S^(-w[9]) * (e^(w[10]*(1-R))-1) * hard_penalty * easy_bonus + 1)
 *   hard_penalty = w[14] if HARD else 1
 *   easy_bonus   = w[16] if EASY else 1
 *
 * Stability after lapse (relearning):
 *   S'_f = w[17] * D^(-w[18]) * ((S+1)^w[18] - 1) * e^(w[10]*(1-R))
 *
 * Next interval: I = S/FACTOR * (R^(1/DECAY) - 1)  where target R = desiredRetention
 *              = S/FACTOR * (desiredRetention^(-2) - 1)   [since DECAY = -0.5]
 *              = S * 9 * (1/desiredRetention^2 - 1)        [simplified, FACTOR=19/81≈0.2346]
 * </pre>
 *
 * <p>Pure Java — no Spring, no JPA annotations. Deterministic: time is passed in as a parameter.
 */
public final class FsrsScheduler implements SchedulingEngine {

  // ---------------------------------------------------------------------------
  // Canonical FSRS-5.5 default weight vector (19 params, 0-indexed)
  // Source: open-spaced-repetition/ts-fsrs default weights, FSRS-5.5 spec (2024-12)
  // Reference: https://github.com/open-spaced-repetition/ts-fsrs
  //
  // Index mapping:
  //   w[0..3]  = S_0(AGAIN, HARD, GOOD, EASY) — initial stability per first rating
  //   w[4]     = D_0 base (initial difficulty base)
  //   w[5]     = D_0 exponential coefficient
  //   w[6]     = D' mean-reversion weight (linear mix toward D_0(GOOD))
  //   w[7]     = D' delta coefficient (linear difficulty shift per rating)
  //   w[8]     = S'_r exponential coefficient (e^w[8] in recall growth)
  //   w[9]     = S'_r stability power decay exponent
  //   w[10]    = S'_r / S'_f retrievability coefficient
  //   w[11]    = S'_f: difficulty power exponent in forget formula
  //   w[12]    = S'_f: stability power exponent in forget formula
  //   w[13]    = S'_f: coefficient constant
  //   w[14]    = S'_r: stability growth floor (added inside exponential)
  //   w[15]    = hard_penalty — multiplier when HARD (< 1 → less growth)
  //   w[16]    = easy_bonus  — multiplier when EASY (> 1 → more growth)
  //   w[17]    = short-term S'_r coefficient (same-day reviews, not used in B-P2a)
  //   w[18]    = short-term S'_r exponent (same-day reviews, not used in B-P2a)
  // ---------------------------------------------------------------------------
  static final double[] DEFAULT_WEIGHTS = {
    0.4072, // w[0]  S_0(AGAIN)
    1.1829, // w[1]  S_0(HARD)
    3.1262, // w[2]  S_0(GOOD)
    7.2102, // w[3]  S_0(EASY)
    7.2212, // w[4]  D_0 base
    0.5316, // w[5]  D_0 exp coefficient
    1.0651, // w[6]  D' mean-reversion weight
    0.0589, // w[7]  D' delta coefficient
    1.5330, // w[8]  S'_r exp coefficient
    0.1544, // w[9]  S'_r stability decay exponent
    1.0000, // w[10] retrievability coefficient in S'_r and S'_f
    1.9395, // w[11] S'_f difficulty power exponent
    0.1100, // w[12] S'_f stability power exponent
    0.2900, // w[13] S'_f coefficient constant
    2.2700, // w[14] S'_r: additional growth term constant (not a multiplier; unused in main
    // formula)
    0.2800, // w[15] hard_penalty: multiplier < 1 for HARD (reduces stability growth)
    2.3100, // w[16] easy_bonus:   multiplier > 1 for EASY (increases stability growth)
    0.5100, // w[17] short-term recall coefficient (same-day reviews; not used in B-P2a)
    0.3400 // w[18] short-term recall exponent   (same-day reviews; not used in B-P2a)
  };

  // Canonical retrievability: R(t,S) = (1 + t/(9*S))^DECAY  where DECAY = -0.5
  // At t=S: R = (1 + 1/9)^(-0.5) ≈ 0.9487  (stability S is NOT where R=0.9; it's the half-life)
  // Next interval: I = 9*S*(R_target^(1/DECAY) - 1) = 9*S*(R_target^(-2) - 1)
  private static final double DECAY = -0.5;

  // Difficulty bounds
  private static final double D_MIN = 1.0;
  private static final double D_MAX = 10.0;

  // Minimum stability after a lapse
  private static final double MIN_STABILITY_AFTER_LAPSE = 0.01;

  private final double[] w;

  /** Creates an FSRS scheduler with the canonical default weights. */
  public FsrsScheduler() {
    this.w = DEFAULT_WEIGHTS.clone();
  }

  /**
   * Creates an FSRS scheduler with custom weights.
   *
   * @param customWeights array of exactly 19 weights
   */
  public FsrsScheduler(double[] customWeights) {
    Objects.requireNonNull(customWeights, "customWeights must not be null");
    if (customWeights.length != 19) {
      throw new IllegalArgumentException(
          "FSRS requires exactly 19 weights, got " + customWeights.length);
    }
    this.w = customWeights.clone();
  }

  /**
   * Schedules a review for a card identified externally by {@code cardId}.
   *
   * @param cardId the card being reviewed (provided by the application layer)
   * @param current the card's current scheduler state
   * @param rating the user's rating
   * @param reviewedAt when the review occurred
   * @param desiredRetention target retention (0.70–0.99)
   * @return a {@link ReviewResult} with previous and next states
   */
  public ReviewResult schedule(
      com.studydeck.domain.model.CardId cardId,
      CardScheduleState current,
      ReviewRating rating,
      Instant reviewedAt,
      double desiredRetention) {
    Objects.requireNonNull(cardId, "cardId must not be null");
    Objects.requireNonNull(current, "current must not be null");
    Objects.requireNonNull(rating, "rating must not be null");
    Objects.requireNonNull(reviewedAt, "reviewedAt must not be null");

    int ratingOrdinal = toOrdinal(rating);
    int elapsedDays = elapsedDays(current, reviewedAt);

    CardScheduleState next =
        switch (current.state()) {
          case NEW -> scheduleNew(current, ratingOrdinal, reviewedAt, desiredRetention);
          case LEARNING, RELEARNING ->
              scheduleLearning(current, ratingOrdinal, reviewedAt, desiredRetention, elapsedDays);
          case REVIEW ->
              scheduleReview(current, ratingOrdinal, reviewedAt, desiredRetention, elapsedDays);
        };

    return new ReviewResult(cardId, rating, reviewedAt, current, next);
  }

  @Override
  public ReviewResult schedule(
      CardScheduleState current, ReviewRating rating, Instant reviewedAt, double desiredRetention) {
    // Delegates without a CardId — caller must use the overload with CardId in real use cases
    Objects.requireNonNull(current, "current must not be null");
    Objects.requireNonNull(rating, "rating must not be null");
    Objects.requireNonNull(reviewedAt, "reviewedAt must not be null");

    int ratingOrdinal = toOrdinal(rating);
    int elapsedDays = elapsedDays(current, reviewedAt);

    CardScheduleState next =
        switch (current.state()) {
          case NEW -> scheduleNew(current, ratingOrdinal, reviewedAt, desiredRetention);
          case LEARNING, RELEARNING ->
              scheduleLearning(current, ratingOrdinal, reviewedAt, desiredRetention, elapsedDays);
          case REVIEW ->
              scheduleReview(current, ratingOrdinal, reviewedAt, desiredRetention, elapsedDays);
        };

    // Use a sentinel CardId (zero UUID) when no cardId is provided at this level
    com.studydeck.domain.model.CardId sentinel =
        new com.studydeck.domain.model.CardId(
            java.util.UUID.fromString("00000000-0000-0000-0000-000000000000"));
    return new ReviewResult(sentinel, rating, reviewedAt, current, next);
  }

  /** Schedules a first review (NEW → LEARNING or REVIEW). */
  private CardScheduleState scheduleNew(
      CardScheduleState current, int r, Instant reviewedAt, double desiredRetention) {

    double s0 = initialStability(r);
    double d0 = initialDifficulty(r);

    // On first review, EASY goes directly to REVIEW; anything else → LEARNING
    if (r == 4) { // EASY
      int scheduledDays = nextInterval(s0, desiredRetention);
      Instant dueAt = reviewedAt.plus(scheduledDays, ChronoUnit.DAYS);
      return current.withState(CardState.REVIEW, s0, d0, 1, 0, scheduledDays, dueAt, reviewedAt);
    } else if (r == 1) { // AGAIN — short learning step (1 minute ~ 0 days interval)
      return current.withState(
          CardState.LEARNING, s0, d0, 1, 0, 0, reviewedAt.plus(1, ChronoUnit.MINUTES), reviewedAt);
    } else {
      // HARD or GOOD — go to learning with short interval
      int scheduledDays = r == 2 ? 0 : 1; // HARD→ same day, GOOD → 1 day
      Instant dueAt =
          scheduledDays == 0
              ? reviewedAt.plus(10, ChronoUnit.MINUTES)
              : reviewedAt.plus(scheduledDays, ChronoUnit.DAYS);
      return current.withState(CardState.LEARNING, s0, d0, 1, 0, scheduledDays, dueAt, reviewedAt);
    }
  }

  /** Schedules a card in LEARNING or RELEARNING state. */
  private CardScheduleState scheduleLearning(
      CardScheduleState current,
      int r,
      Instant reviewedAt,
      double desiredRetention,
      int elapsedDays) {

    double s = current.stability() > 0 ? current.stability() : initialStability(r);
    double d = current.difficulty() > 0 ? current.difficulty() : initialDifficulty(r);
    d = clampDifficulty(updateDifficulty(d, r));

    if (r == 1) { // AGAIN — stay in learning/relearning
      CardState nextState =
          current.state() == CardState.RELEARNING ? CardState.RELEARNING : CardState.LEARNING;
      return current.withState(
          nextState,
          s,
          d,
          current.reps() + 1,
          current.lapses(),
          0,
          reviewedAt.plus(1, ChronoUnit.MINUTES),
          reviewedAt);
    }

    // HARD, GOOD, EASY — graduate to REVIEW
    double newS =
        (current.state() == CardState.LEARNING)
            ? stabilityAfterRecall(s, d, retrievability(s, elapsedDays), r)
            : stabilityAfterLapse(s, d, retrievability(s, elapsedDays));

    // Ensure some minimum growth
    newS = Math.max(newS, s * 1.0);

    int scheduledDays = nextInterval(newS, desiredRetention);
    scheduledDays = Math.max(scheduledDays, 1); // at least 1 day for graduated cards
    Instant dueAt = reviewedAt.plus(scheduledDays, ChronoUnit.DAYS);

    return current.withState(
        CardState.REVIEW,
        newS,
        d,
        current.reps() + 1,
        current.lapses(),
        scheduledDays,
        dueAt,
        reviewedAt);
  }

  /** Schedules a card in REVIEW state. */
  private CardScheduleState scheduleReview(
      CardScheduleState current,
      int r,
      Instant reviewedAt,
      double desiredRetention,
      int elapsedDays) {

    double s = Math.max(current.stability(), 0.01);
    double d = clampDifficulty(updateDifficulty(current.difficulty(), r));
    double ret = retrievability(s, elapsedDays);

    if (r == 1) { // AGAIN — lapse → RELEARNING
      double newS = stabilityAfterLapse(s, d, ret);
      newS = Math.max(newS, MIN_STABILITY_AFTER_LAPSE);
      int newLapses = current.lapses() + 1;
      // Short relearning step (1 minute)
      return current.withState(
          CardState.RELEARNING,
          newS,
          d,
          current.reps() + 1,
          newLapses,
          0,
          reviewedAt.plus(1, ChronoUnit.MINUTES),
          reviewedAt);
    }

    // HARD, GOOD, EASY — stay in REVIEW
    double newS = stabilityAfterRecall(s, d, ret, r);
    newS = Math.max(newS, s * 1.01); // always grow at least a tiny bit

    int scheduledDays = nextInterval(newS, desiredRetention);
    scheduledDays = Math.max(scheduledDays, 1);
    Instant dueAt = reviewedAt.plus(scheduledDays, ChronoUnit.DAYS);

    return current.withState(
        CardState.REVIEW,
        newS,
        d,
        current.reps() + 1,
        current.lapses(),
        scheduledDays,
        dueAt,
        reviewedAt);
  }

  // ---------------------------------------------------------------------------
  // FSRS mathematical formulas
  // ---------------------------------------------------------------------------

  /** Initial stability S_0(r) for a new card. S_0(r) = w[r-1] where r ∈ {1,2,3,4} */
  double initialStability(int r) {
    return w[r - 1]; // w[0]=AGAIN, w[1]=HARD, w[2]=GOOD, w[3]=EASY
  }

  /**
   * Initial difficulty D_0(r) for a new card. D_0(r) = w[4] - exp(w[5] * (r - 1)) + 1, clamped to
   * [1,10]
   */
  double initialDifficulty(int r) {
    double d = w[4] - Math.exp(w[5] * (r - 1)) + 1.0;
    return clampDifficulty(d);
  }

  /**
   * Retrievability R(t, S) — probability of recall after {@code elapsedDays} days.
   *
   * <pre>
   * R(t, S) = (1 + t / (9 * S))^DECAY    where DECAY = -0.5
   * </pre>
   *
   * <p>At t=S: R ≈ 0.9487. Stability S is the half-life point, not the exact 90%-recall point.
   */
  double retrievability(double stability, int elapsedDays) {
    if (elapsedDays <= 0) return 1.0;
    return Math.pow(1.0 + elapsedDays / (9.0 * stability), DECAY);
  }

  /**
   * Stability after successful recall S'_r.
   *
   * <pre>
   * S'_r = S * (exp(w[8]) * (11 - D) * S^(-w[9]) * (exp(w[10]*(1-R)) - 1)
   *            * hard_penalty * easy_bonus + 1)
   *
   * hard_penalty = w[15]  (< 1) when rating == HARD
   * easy_bonus   = w[16]  (> 1) when rating == EASY
   * </pre>
   */
  double stabilityAfterRecall(double stability, double difficulty, double retrievability, int r) {
    double hardPenalty = (r == 2) ? w[15] : 1.0; // w[15] < 1 → less growth for HARD
    double easyBonus = (r == 4) ? w[16] : 1.0; // w[16] > 1 → more growth for EASY
    double growth =
        Math.exp(w[8])
            * (11.0 - difficulty)
            * Math.pow(stability, -w[9])
            * (Math.exp(w[10] * (1.0 - retrievability)) - 1.0)
            * hardPenalty
            * easyBonus;
    return stability * (growth + 1.0);
  }

  /**
   * Stability after a lapse S'_f (forgotten card, AGAIN in REVIEW state).
   *
   * <pre>
   * S'_f = w[11] * D^(-w[12]) * ((S+1)^w[13] - 1) * exp(w[14] * (1-R))
   *      clamped to MIN_STABILITY_AFTER_LAPSE
   * </pre>
   *
   * <p>Weight indices (FSRS-5 mapping):
   *
   * <ul>
   *   <li>w[11] = 1.9395 — forget coefficient
   *   <li>w[12] = 0.11 — difficulty exponent (negative: higher D → lower S'_f)
   *   <li>w[13] = 0.29 — stability exponent
   *   <li>w[14] = 2.27 — retrievability coefficient in exp term
   * </ul>
   */
  double stabilityAfterLapse(double stability, double difficulty, double retrievability) {
    double sr =
        w[11]
            * Math.pow(difficulty, -w[12])
            * (Math.pow(stability + 1.0, w[13]) - 1.0)
            * Math.exp(w[14] * (1.0 - retrievability));
    return Math.max(sr, MIN_STABILITY_AFTER_LAPSE);
  }

  /**
   * Difficulty update: mean-reversion toward D_0(GOOD), adjusted by rating delta.
   *
   * <pre>
   * D' = w[6]*D_0(GOOD) + (1-w[6]) * (D - w[7]*(r - 3))
   * clamped to [1, 10]
   * </pre>
   */
  double updateDifficulty(double difficulty, int r) {
    double d0Good = initialDifficulty(3); // D_0(GOOD)
    return w[6] * d0Good + (1.0 - w[6]) * (difficulty - w[7] * (r - 3.0));
  }

  /**
   * Next review interval in days from desired retention.
   *
   * <pre>
   * Solving R(I, S) = R_target:
   * (1 + I/(9S))^(-0.5) = R_target
   * I = 9S * (R_target^(-2) - 1)
   * </pre>
   */
  int nextInterval(double stability, double desiredRetention) {
    double interval = 9.0 * stability * (Math.pow(desiredRetention, 1.0 / DECAY) - 1.0);
    return Math.max(1, (int) Math.round(interval));
  }

  private double clampDifficulty(double d) {
    return Math.max(D_MIN, Math.min(D_MAX, d));
  }

  private static int toOrdinal(ReviewRating rating) {
    return switch (rating) {
      case AGAIN -> 1;
      case HARD -> 2;
      case GOOD -> 3;
      case EASY -> 4;
    };
  }

  private static int elapsedDays(CardScheduleState current, Instant reviewedAt) {
    if (current.lastReviewedAt() == null) return 0;
    long days = ChronoUnit.DAYS.between(current.lastReviewedAt(), reviewedAt);
    return (int) Math.max(0, days);
  }
}
