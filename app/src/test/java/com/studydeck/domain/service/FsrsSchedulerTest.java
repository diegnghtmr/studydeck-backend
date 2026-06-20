package com.studydeck.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.studydeck.domain.model.CardId;
import com.studydeck.domain.model.CardScheduleState;
import com.studydeck.domain.model.CardState;
import com.studydeck.domain.model.ReviewRating;
import com.studydeck.domain.model.ReviewResult;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exhaustive unit tests for FsrsScheduler correctness.
 *
 * <p>Reference values are derived from the FSRS-5.5 spec using the canonical default weights.
 * Tolerances of ±0.05 are used for floating-point stability comparisons and ±1 for interval days.
 */
class FsrsSchedulerTest {

  private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
  private static final CardId CARD = new CardId(UUID.randomUUID());
  private static final double RETENTION = 0.9;
  private static final double TOLERANCE = 0.05;

  private FsrsScheduler fsrs;
  private CardScheduleState newCard;

  @BeforeEach
  void setUp() {
    fsrs = new FsrsScheduler();
    newCard = CardScheduleState.newFsrsCard(T0);
  }

  // -----------------------------------------------------------------------
  // Initial stability S_0 — canonical FSRS-5.5 weight values
  // -----------------------------------------------------------------------

  @Test
  void initialStabilityAgainMatchesWeight0() {
    assertThat(fsrs.initialStability(1)).isCloseTo(0.4072, within(TOLERANCE));
  }

  @Test
  void initialStabilityHardMatchesWeight1() {
    assertThat(fsrs.initialStability(2)).isCloseTo(1.1829, within(TOLERANCE));
  }

  @Test
  void initialStabilityGoodMatchesWeight2() {
    assertThat(fsrs.initialStability(3)).isCloseTo(3.1262, within(TOLERANCE));
  }

  @Test
  void initialStabilityEasyMatchesWeight3() {
    assertThat(fsrs.initialStability(4)).isCloseTo(7.2102, within(TOLERANCE));
  }

  // -----------------------------------------------------------------------
  // Initial difficulty D_0 — formula D_0(r) = w[4] - exp(w[5]*(r-1)) + 1
  // -----------------------------------------------------------------------

  @Test
  void initialDifficultyForGoodIsNearFive() {
    // D_0(3) should be around the mid-range
    double d = fsrs.initialDifficulty(3);
    assertThat(d).isGreaterThanOrEqualTo(1.0).isLessThanOrEqualTo(10.0);
  }

  @Test
  void initialDifficultyAgainIsHigherThanEasy() {
    // Higher rating (EASY) should produce LOWER difficulty
    double dAgain = fsrs.initialDifficulty(1);
    double dEasy = fsrs.initialDifficulty(4);
    assertThat(dAgain).isGreaterThan(dEasy);
  }

  @Test
  void initialDifficultyIsWithinBounds() {
    for (int r = 1; r <= 4; r++) {
      double d = fsrs.initialDifficulty(r);
      assertThat(d)
          .as("D_0(%d) must be in [1,10]", r)
          .isGreaterThanOrEqualTo(1.0)
          .isLessThanOrEqualTo(10.0);
    }
  }

  // -----------------------------------------------------------------------
  // Retrievability R(t, S)
  // -----------------------------------------------------------------------

  @Test
  void retrievabilityAtTimeZeroIsOne() {
    assertThat(fsrs.retrievability(5.0, 0)).isCloseTo(1.0, within(0.001));
  }

  @Test
  void retrievabilityDecaysOverTime() {
    double r1 = fsrs.retrievability(5.0, 1);
    double r5 = fsrs.retrievability(5.0, 5);
    double r10 = fsrs.retrievability(5.0, 10);
    assertThat(r1).isGreaterThan(r5);
    assertThat(r5).isGreaterThan(r10);
  }

  @Test
  void retrievabilityAtStabilityDaysIsApprox94Percent() {
    // R(t=S, S) = (1 + S/(9*S))^(-0.5) = (1 + 1/9)^(-0.5) = (10/9)^(-0.5) ≈ 0.9487
    // Stability S is NOT the "90% recall" point; it's the memory half-life in the FSRS model.
    double r = fsrs.retrievability(5.0, 5);
    assertThat(r).isCloseTo(0.9487, within(0.01));
  }

  @Test
  void higherStabilityMeansSlowerDecay() {
    double rLow = fsrs.retrievability(1.0, 5);
    double rHigh = fsrs.retrievability(10.0, 5);
    assertThat(rHigh).isGreaterThan(rLow);
  }

  // -----------------------------------------------------------------------
  // Next interval monotonicity
  // -----------------------------------------------------------------------

  @Test
  void higherStabilityProducesLongerInterval() {
    int i1 = fsrs.nextInterval(1.0, RETENTION);
    int i5 = fsrs.nextInterval(5.0, RETENTION);
    int i20 = fsrs.nextInterval(20.0, RETENTION);
    assertThat(i1).isLessThan(i5);
    assertThat(i5).isLessThan(i20);
  }

  @Test
  void higherDesiredRetentionProducesShorterInterval() {
    int intervalLow = fsrs.nextInterval(5.0, 0.70);
    int intervalHigh = fsrs.nextInterval(5.0, 0.95);
    assertThat(intervalHigh).isLessThan(intervalLow);
  }

  @Test
  void nextIntervalIsAtLeastOne() {
    assertThat(fsrs.nextInterval(0.01, 0.99)).isGreaterThanOrEqualTo(1);
  }

  // -----------------------------------------------------------------------
  // NEW card → first review for each rating
  // -----------------------------------------------------------------------

  @Test
  void newCardFirstReviewAgainGoesToLearning() {
    ReviewResult result = fsrs.schedule(newCard, ReviewRating.AGAIN, T0, RETENTION);

    assertThat(result.nextState().state()).isEqualTo(CardState.LEARNING);
    assertThat(result.nextState().reps()).isEqualTo(1);
    assertThat(result.nextState().lapses()).isEqualTo(0);
    assertThat(result.nextState().stability()).isCloseTo(0.4072, within(TOLERANCE));
  }

  @Test
  void newCardFirstReviewHardGoesToLearning() {
    ReviewResult result = fsrs.schedule(newCard, ReviewRating.HARD, T0, RETENTION);

    assertThat(result.nextState().state()).isEqualTo(CardState.LEARNING);
    assertThat(result.nextState().stability()).isCloseTo(1.1829, within(TOLERANCE));
  }

  @Test
  void newCardFirstReviewGoodGoesToLearning() {
    ReviewResult result = fsrs.schedule(newCard, ReviewRating.GOOD, T0, RETENTION);

    assertThat(result.nextState().state()).isEqualTo(CardState.LEARNING);
    assertThat(result.nextState().stability()).isCloseTo(3.1262, within(TOLERANCE));
    assertThat(result.nextState().scheduledDays()).isEqualTo(1);
  }

  @Test
  void newCardFirstReviewEasyGoesDirectlyToReview() {
    ReviewResult result = fsrs.schedule(newCard, ReviewRating.EASY, T0, RETENTION);

    assertThat(result.nextState().state()).isEqualTo(CardState.REVIEW);
    assertThat(result.nextState().stability()).isCloseTo(7.2102, within(TOLERANCE));
    assertThat(result.nextState().scheduledDays()).isGreaterThan(0);
  }

  // -----------------------------------------------------------------------
  // Interval ordering for first review (EASY ≥ GOOD ≥ HARD)
  // -----------------------------------------------------------------------

  @Test
  void firstReviewIntervalMonotonicityEasyGoodHard() {
    int hardInterval =
        fsrs.schedule(newCard, ReviewRating.HARD, T0, RETENTION).nextState().scheduledDays();
    int goodInterval =
        fsrs.schedule(newCard, ReviewRating.GOOD, T0, RETENTION).nextState().scheduledDays();
    int easyInterval =
        fsrs.schedule(newCard, ReviewRating.EASY, T0, RETENTION).nextState().scheduledDays();

    assertThat(easyInterval).isGreaterThanOrEqualTo(goodInterval);
    assertThat(goodInterval).isGreaterThanOrEqualTo(hardInterval);
  }

  // -----------------------------------------------------------------------
  // State machine: LEARNING → REVIEW graduation
  // -----------------------------------------------------------------------

  @Test
  void learningCardGoodGraduatesToReview() {
    // First review → LEARNING
    ReviewResult r1 = fsrs.schedule(newCard, ReviewRating.GOOD, T0, RETENTION);
    CardScheduleState learning = r1.nextState();
    assertThat(learning.state()).isEqualTo(CardState.LEARNING);

    // Second review → should graduate to REVIEW
    Instant t1 = T0.plus(1, ChronoUnit.DAYS);
    ReviewResult r2 = fsrs.schedule(learning, ReviewRating.GOOD, t1, RETENTION);
    assertThat(r2.nextState().state()).isEqualTo(CardState.REVIEW);
    assertThat(r2.nextState().scheduledDays()).isGreaterThanOrEqualTo(1);
  }

  @Test
  void learningCardAgainStaysInLearning() {
    ReviewResult r1 = fsrs.schedule(newCard, ReviewRating.GOOD, T0, RETENTION);
    CardScheduleState learning = r1.nextState();

    ReviewResult r2 = fsrs.schedule(learning, ReviewRating.AGAIN, T0, RETENTION);
    assertThat(r2.nextState().state()).isEqualTo(CardState.LEARNING);
  }

  // -----------------------------------------------------------------------
  // State machine: REVIEW → RELEARNING on AGAIN (lapse)
  // -----------------------------------------------------------------------

  @Test
  void reviewCardAgainGoesToRelearning() {
    // Build a REVIEW state card
    CardScheduleState reviewState = buildReviewCard(10.0, 5.0);

    ReviewResult result = fsrs.schedule(reviewState, ReviewRating.AGAIN, T0, RETENTION);

    assertThat(result.nextState().state()).isEqualTo(CardState.RELEARNING);
    assertThat(result.nextState().lapses()).isEqualTo(1);
  }

  @Test
  void lapseDecrementsStability() {
    CardScheduleState reviewState = buildReviewCard(10.0, 5.0);

    ReviewResult result = fsrs.schedule(reviewState, ReviewRating.AGAIN, T0, RETENTION);

    assertThat(result.nextState().stability()).isLessThan(10.0);
  }

  @Test
  void consecutiveLapsesAccumulate() {
    CardScheduleState reviewState = buildReviewCard(10.0, 5.0);
    ReviewResult r1 = fsrs.schedule(reviewState, ReviewRating.AGAIN, T0, RETENTION);
    // Card is now RELEARNING; graduate it back to REVIEW
    CardScheduleState reviewState2 = buildReviewCardFrom(r1.nextState());
    ReviewResult r2 =
        fsrs.schedule(reviewState2, ReviewRating.AGAIN, T0.plus(5, ChronoUnit.DAYS), RETENTION);

    assertThat(r2.nextState().lapses()).isEqualTo(2);
  }

  // -----------------------------------------------------------------------
  // State machine: RELEARNING → REVIEW
  // -----------------------------------------------------------------------

  @Test
  void relearningCardGoodGraduatesToReview() {
    CardScheduleState relearning = buildRelearningCard(5.0, 5.0);
    ReviewResult result = fsrs.schedule(relearning, ReviewRating.GOOD, T0, RETENTION);

    assertThat(result.nextState().state()).isEqualTo(CardState.REVIEW);
  }

  // -----------------------------------------------------------------------
  // Difficulty stays within bounds
  // -----------------------------------------------------------------------

  @Test
  void difficultyNeverExceedsTen() {
    CardScheduleState state = newCard;
    Instant t = T0;
    for (int i = 0; i < 20; i++) {
      ReviewResult r = fsrs.schedule(state, ReviewRating.AGAIN, t, RETENTION);
      state = r.nextState();
      t = t.plus(1, ChronoUnit.DAYS);
      assertThat(state.difficulty())
          .as("Difficulty must be <= 10 after %d reviews", i + 1)
          .isLessThanOrEqualTo(10.0);
    }
  }

  @Test
  void difficultyNeverBelowOne() {
    CardScheduleState state = newCard;
    Instant t = T0;
    for (int i = 0; i < 20; i++) {
      ReviewResult r = fsrs.schedule(state, ReviewRating.EASY, t, RETENTION);
      state = r.nextState();
      t = t.plus(state.scheduledDays(), ChronoUnit.DAYS);
      assertThat(state.difficulty())
          .as("Difficulty must be >= 1 after %d EASY reviews", i + 1)
          .isGreaterThanOrEqualTo(1.0);
    }
  }

  // -----------------------------------------------------------------------
  // Determinism — same inputs → same outputs
  // -----------------------------------------------------------------------

  @Test
  void deterministicSameInputsSameOutputs() {
    ReviewResult r1 = fsrs.schedule(newCard, ReviewRating.GOOD, T0, RETENTION);
    ReviewResult r2 = fsrs.schedule(newCard, ReviewRating.GOOD, T0, RETENTION);

    assertThat(r1.nextState().stability()).isEqualTo(r2.nextState().stability());
    assertThat(r1.nextState().difficulty()).isEqualTo(r2.nextState().difficulty());
    assertThat(r1.nextState().scheduledDays()).isEqualTo(r2.nextState().scheduledDays());
    assertThat(r1.nextState().dueAt()).isEqualTo(r2.nextState().dueAt());
  }

  // -----------------------------------------------------------------------
  // Higher desiredRetention → shorter intervals
  // -----------------------------------------------------------------------

  @Test
  void higherRetentionYieldsShorterInterval() {
    CardScheduleState reviewCard = buildReviewCard(10.0, 5.0);

    ReviewResult resultLow = fsrs.schedule(reviewCard, ReviewRating.GOOD, T0, 0.70);
    ReviewResult resultHigh = fsrs.schedule(reviewCard, ReviewRating.GOOD, T0, 0.95);

    assertThat(resultHigh.nextState().scheduledDays())
        .isLessThan(resultLow.nextState().scheduledDays());
  }

  // -----------------------------------------------------------------------
  // Stability grows with successful recalls (monotonicity check)
  // -----------------------------------------------------------------------

  @Test
  void stabilityGrowsWithSuccessfulReviews() {
    CardScheduleState state = newCard;
    Instant t = T0;
    double prevStability = 0;

    ReviewResult r1 = fsrs.schedule(state, ReviewRating.GOOD, t, RETENTION);
    state = r1.nextState();
    t = t.plus(Math.max(1, state.scheduledDays()), ChronoUnit.DAYS);

    // Graduate to REVIEW first
    ReviewResult r2 = fsrs.schedule(state, ReviewRating.GOOD, t, RETENTION);
    state = r2.nextState();
    double stabilityAfterGraduation = state.stability();

    for (int i = 0; i < 5; i++) {
      t = t.plus(state.scheduledDays(), ChronoUnit.DAYS);
      ReviewResult r = fsrs.schedule(state, ReviewRating.GOOD, t, RETENTION);
      state = r.nextState();
      assertThat(state.stability())
          .as("Stability must grow after GOOD on iteration %d", i)
          .isGreaterThan(stabilityAfterGraduation);
      stabilityAfterGraduation = state.stability();
    }
  }

  // -----------------------------------------------------------------------
  // ReviewResult carries correct previous/next state and cardId
  // -----------------------------------------------------------------------

  @Test
  void reviewResultPreviousStateMatchesInput() {
    ReviewResult result = fsrs.schedule(CARD, newCard, ReviewRating.GOOD, T0, RETENTION);

    assertThat(result.previousState()).isEqualTo(newCard);
    assertThat(result.cardId()).isEqualTo(CARD);
    assertThat(result.rating()).isEqualTo(ReviewRating.GOOD);
    assertThat(result.reviewedAt()).isEqualTo(T0);
  }

  @Test
  void reviewResultNextStateHasCorrectAlgorithm() {
    ReviewResult result = fsrs.schedule(newCard, ReviewRating.EASY, T0, RETENTION);

    assertThat(result.nextState().algorithm())
        .isEqualTo(com.studydeck.domain.model.SchedulerAlgorithm.FSRS);
  }

  // -----------------------------------------------------------------------
  // ReviewResult.toReviewLog
  // -----------------------------------------------------------------------

  @Test
  void reviewResultBuildsCorrectReviewLog() {
    ReviewResult result = fsrs.schedule(CARD, newCard, ReviewRating.GOOD, T0, RETENTION);
    com.studydeck.domain.model.ReviewLog log = result.toReviewLog(0);

    assertThat(log.cardId()).isEqualTo(CARD);
    assertThat(log.rating()).isEqualTo(ReviewRating.GOOD);
    assertThat(log.stateBefore()).isEqualTo(CardState.NEW);
    assertThat(log.reviewedAt()).isEqualTo(T0);
    assertThat(log.elapsedDays()).isEqualTo(0);
    assertThat(log.scheduledDays()).isEqualTo(result.nextState().scheduledDays());
  }

  // -----------------------------------------------------------------------
  // Null argument rejection
  // -----------------------------------------------------------------------

  @Test
  void nullCurrentStateIsRejected() {
    assertThatThrownBy(() -> fsrs.schedule(null, ReviewRating.GOOD, T0, RETENTION))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void nullRatingIsRejected() {
    assertThatThrownBy(() -> fsrs.schedule(newCard, null, T0, RETENTION))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void nullReviewedAtIsRejected() {
    assertThatThrownBy(() -> fsrs.schedule(newCard, ReviewRating.GOOD, null, RETENTION))
        .isInstanceOf(NullPointerException.class);
  }

  // -----------------------------------------------------------------------
  // Concrete FSRS-5.5 reference values (new card, GOOD, 0.9 retention)
  // new card GOOD → LEARNING with stability ≈ 3.13 → interval = 1 day
  // After graduating (second GOOD) → REVIEW, stability grows, interval ≈ 3-7 days
  // -----------------------------------------------------------------------

  @Test
  void newCardGoodProducesExpectedInitialStability() {
    ReviewResult result = fsrs.schedule(newCard, ReviewRating.GOOD, T0, RETENTION);
    // S_0(GOOD) = w[2] = 3.1262
    assertThat(result.nextState().stability()).isCloseTo(3.1262, within(TOLERANCE));
  }

  @Test
  void newCardEasyProducesExpectedInitialStabilityAndInterval() {
    ReviewResult result = fsrs.schedule(newCard, ReviewRating.EASY, T0, RETENTION);
    // S_0(EASY) = w[3] = 7.2102
    assertThat(result.nextState().stability()).isCloseTo(7.2102, within(TOLERANCE));
    // With S=7.21, R_target=0.9: I = 7.21/0.2346*(0.9^-2 - 1) ≈ 7.21/0.2346 * 0.2346 = ~7 days
    assertThat(result.nextState().scheduledDays()).isGreaterThan(0);
  }

  // -----------------------------------------------------------------------
  // Custom weights constructor
  // -----------------------------------------------------------------------

  @Test
  void customWeightsAreRespected() {
    double[] customWeights = FsrsScheduler.DEFAULT_WEIGHTS.clone();
    customWeights[2] = 5.0; // S_0(GOOD) = 5.0 instead of 3.1262
    FsrsScheduler customFsrs = new FsrsScheduler(customWeights);

    ReviewResult result = customFsrs.schedule(newCard, ReviewRating.GOOD, T0, RETENTION);
    assertThat(result.nextState().stability()).isCloseTo(5.0, within(TOLERANCE));
  }

  @Test
  void wrongWeightCountThrows() {
    assertThatThrownBy(() -> new FsrsScheduler(new double[10]))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("19");
  }

  // -----------------------------------------------------------------------
  // Stability-after-lapse formula check
  // -----------------------------------------------------------------------

  @Test
  void lapseStabilityIsPositiveAndLessThanPrevious() {
    double stability = 10.0;
    double difficulty = 5.0;
    double ret = fsrs.retrievability(stability, 5);
    double lapseS = fsrs.stabilityAfterLapse(stability, difficulty, ret);

    assertThat(lapseS).isGreaterThan(0.0);
    assertThat(lapseS).isLessThan(stability);
  }

  // -----------------------------------------------------------------------
  // Stability-after-recall: EASY ≥ GOOD ≥ HARD (ordering)
  // -----------------------------------------------------------------------

  @Test
  void recallStabilityOrderingEasyGoodHard() {
    double s = 5.0;
    double d = 5.0;
    double ret = 0.9;

    double sHard = fsrs.stabilityAfterRecall(s, d, ret, 2);
    double sGood = fsrs.stabilityAfterRecall(s, d, ret, 3);
    double sEasy = fsrs.stabilityAfterRecall(s, d, ret, 4);

    assertThat(sEasy).isGreaterThanOrEqualTo(sGood);
    assertThat(sGood).isGreaterThan(sHard);
  }

  // -----------------------------------------------------------------------
  // Helper builders
  // -----------------------------------------------------------------------

  private CardScheduleState buildReviewCard(double stability, double difficulty) {
    return new CardScheduleState(
        com.studydeck.domain.model.SchedulerAlgorithm.FSRS,
        CardState.REVIEW,
        stability,
        difficulty,
        RETENTION,
        3,
        0,
        5,
        T0,
        T0.minus(5, ChronoUnit.DAYS));
  }

  private CardScheduleState buildRelearningCard(double stability, double difficulty) {
    return new CardScheduleState(
        com.studydeck.domain.model.SchedulerAlgorithm.FSRS,
        CardState.RELEARNING,
        stability,
        difficulty,
        RETENTION,
        5,
        1,
        0,
        T0,
        T0.minus(1, ChronoUnit.DAYS));
  }

  private CardScheduleState buildReviewCardFrom(CardScheduleState base) {
    return new CardScheduleState(
        base.algorithm(),
        CardState.REVIEW,
        base.stability(),
        base.difficulty(),
        base.desiredRetention(),
        base.reps(),
        base.lapses(),
        5,
        T0.plus(5, ChronoUnit.DAYS),
        T0);
  }
}
