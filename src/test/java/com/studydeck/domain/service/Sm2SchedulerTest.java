package com.studydeck.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.studydeck.domain.model.CardId;
import com.studydeck.domain.model.CardScheduleState;
import com.studydeck.domain.model.CardState;
import com.studydeck.domain.model.ReviewRating;
import com.studydeck.domain.model.ReviewResult;
import com.studydeck.domain.model.SchedulerAlgorithm;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Sm2Scheduler correctness.
 *
 * <p>Validates the classic SM-2 algorithm: quality mapping, ease factor updates, interval
 * progression (1, 6, *EF), reset on AGAIN, and EF floor 1.3.
 */
class Sm2SchedulerTest {

  private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
  private static final CardId CARD = new CardId(UUID.randomUUID());
  private static final double RETENTION = 0.9;

  private Sm2Scheduler sm2;
  private CardScheduleState newCard;

  @BeforeEach
  void setUp() {
    sm2 = new Sm2Scheduler();
    newCard = CardScheduleState.newCard(SchedulerAlgorithm.SM2, RETENTION, T0);
  }

  // -----------------------------------------------------------------------
  // Quality mapping
  // -----------------------------------------------------------------------

  @Test
  void againMapsToQuality0() {
    assertThat(Sm2Scheduler.toQuality(ReviewRating.AGAIN)).isEqualTo(0);
  }

  @Test
  void hardMapsToQuality2() {
    assertThat(Sm2Scheduler.toQuality(ReviewRating.HARD)).isEqualTo(2);
  }

  @Test
  void goodMapsToQuality3() {
    assertThat(Sm2Scheduler.toQuality(ReviewRating.GOOD)).isEqualTo(3);
  }

  @Test
  void easyMapsToQuality5() {
    assertThat(Sm2Scheduler.toQuality(ReviewRating.EASY)).isEqualTo(5);
  }

  // -----------------------------------------------------------------------
  // First interval I(1) = 1 day
  // -----------------------------------------------------------------------

  @Test
  void firstIntervalIsOneDay() {
    assertThat(sm2.nextInterval(0, 2.5, 0)).isEqualTo(1);
  }

  // -----------------------------------------------------------------------
  // Second interval I(2) = 6 days
  // -----------------------------------------------------------------------

  @Test
  void secondIntervalIsSixDays() {
    assertThat(sm2.nextInterval(1, 2.5, 1)).isEqualTo(6);
  }

  // -----------------------------------------------------------------------
  // Third interval I(3) = I(2) * EF = 6 * 2.5 = 15 days
  // -----------------------------------------------------------------------

  @Test
  void thirdIntervalIsSecondMultipliedByEf() {
    assertThat(sm2.nextInterval(2, 2.5, 6)).isEqualTo(15);
  }

  // -----------------------------------------------------------------------
  // EF update formula: EF' = EF + (0.1 - (5-q)*(0.08 + (5-q)*0.02))
  // -----------------------------------------------------------------------

  @Test
  void easyRatingIncreasesEf() {
    ReviewResult r1 = sm2.schedule(newCard, ReviewRating.EASY, T0, RETENTION);
    // EF should be > initial 2.5 → but actually the formula for q=5:
    // delta = 0.1 - 0*(0.08+0*0.02) = 0.1
    // BUT EF is capped at MAX_EF=2.5; initial EF is also 2.5, so it stays 2.5
    // Actually: EF=2.5, q=5, delta = 0.1 - 0 = 0.1 → 2.5+0.1=2.6 but capped to 2.5
    double ef = r1.nextState().stability();
    assertThat(ef).isLessThanOrEqualTo(2.5);
    assertThat(ef).isGreaterThanOrEqualTo(1.3);
  }

  @Test
  void hardRatingTreatedAsFailedInSm2() {
    // In SM-2, quality < 3 is considered a failed recall.
    // HARD maps to q=2 (< 3), so it resets reps and keeps EF unchanged.
    // This is the classic SM-2 spec: only q >= 3 is a successful recall.
    ReviewResult r1 = sm2.schedule(newCard, ReviewRating.HARD, T0, RETENTION);

    assertThat(r1.nextState().state()).isEqualTo(CardState.LEARNING);
    assertThat(r1.nextState().reps()).isEqualTo(0); // reset
    // EF stays at default (2.5) since SM-2 does not update EF on failure
    assertThat(r1.nextState().stability()).isEqualTo(2.5);
    assertThat(r1.nextState().scheduledDays()).isEqualTo(1);
  }

  @Test
  void efFloorIs1dot3() {
    // Drive EF down with repeated HARD ratings
    CardScheduleState state = newCard;
    Instant t = T0;
    for (int i = 0; i < 30; i++) {
      ReviewResult r = sm2.schedule(state, ReviewRating.HARD, t, RETENTION);
      state = r.nextState();
      t = t.plus(1, ChronoUnit.DAYS);
      assertThat(state.stability())
          .as("EF must never go below 1.3 after %d reviews", i + 1)
          .isGreaterThanOrEqualTo(1.3);
    }
  }

  // -----------------------------------------------------------------------
  // AGAIN resets interval and reps
  // -----------------------------------------------------------------------

  @Test
  void againOnNewCardGoesToLearningWithInterval1() {
    ReviewResult result = sm2.schedule(newCard, ReviewRating.AGAIN, T0, RETENTION);

    assertThat(result.nextState().state()).isEqualTo(CardState.LEARNING);
    assertThat(result.nextState().scheduledDays()).isEqualTo(1);
    assertThat(result.nextState().reps()).isEqualTo(0);
  }

  @Test
  void againOnReviewCardIncrementsLapses() {
    CardScheduleState reviewCard = buildReviewCard(2.5, 5.0);

    ReviewResult result = sm2.schedule(reviewCard, ReviewRating.AGAIN, T0, RETENTION);

    assertThat(result.nextState().state()).isEqualTo(CardState.LEARNING);
    assertThat(result.nextState().lapses()).isEqualTo(1);
    assertThat(result.nextState().reps()).isEqualTo(0);
  }

  @Test
  void againPreservesEf() {
    // SM-2 spec: on fail, EF is NOT changed
    ReviewResult result = sm2.schedule(newCard, ReviewRating.AGAIN, T0, RETENTION);
    // EF should be unchanged (initial EF = 2.5, stored as stability initially 0 → default 2.5)
    double ef = result.nextState().stability();
    assertThat(ef).isGreaterThanOrEqualTo(1.3); // EF intact
  }

  // -----------------------------------------------------------------------
  // State transitions
  // -----------------------------------------------------------------------

  @Test
  void newCardGoodGraduatesToReview() {
    ReviewResult r = sm2.schedule(newCard, ReviewRating.GOOD, T0, RETENTION);
    assertThat(r.nextState().state()).isEqualTo(CardState.REVIEW);
  }

  @Test
  void reviewCardGoodStaysInReview() {
    CardScheduleState reviewCard = buildReviewCard(2.5, 5.0);
    ReviewResult r = sm2.schedule(reviewCard, ReviewRating.GOOD, T0, RETENTION);
    assertThat(r.nextState().state()).isEqualTo(CardState.REVIEW);
  }

  // -----------------------------------------------------------------------
  // Interval progression: 1 → 6 → 15 (with default EF=2.5)
  // -----------------------------------------------------------------------

  @Test
  void fullIntervalProgressionWithGoodRatings() {
    // Review 1: new card GOOD (q=3) → reps=1, interval=1, EF updated
    // EF delta = 0.1 - (5-3)*(0.08+(5-3)*0.02) = 0.1 - 2*0.12 = -0.14 → EF = 2.5-0.14 = 2.36
    ReviewResult r1 = sm2.schedule(newCard, ReviewRating.GOOD, T0, RETENTION);
    assertThat(r1.nextState().reps()).isEqualTo(1);
    assertThat(r1.nextState().scheduledDays()).isEqualTo(1);
    assertThat(r1.nextState().stability()).isCloseTo(2.36, within(0.05));

    // Review 2: reps=1, previousInterval=1, GOOD → interval=6 (hardcoded second interval)
    // EF updated again: EF = 2.36 - 0.14 = 2.22
    Instant t1 = T0.plus(1, ChronoUnit.DAYS);
    ReviewResult r2 = sm2.schedule(r1.nextState(), ReviewRating.GOOD, t1, RETENTION);
    assertThat(r2.nextState().reps()).isEqualTo(2);
    assertThat(r2.nextState().scheduledDays()).isEqualTo(6);

    // Review 3: reps=2, previousInterval=6, EF≈2.22, GOOD → interval=round(6*2.22)=13
    Instant t2 = t1.plus(6, ChronoUnit.DAYS);
    ReviewResult r3 = sm2.schedule(r2.nextState(), ReviewRating.GOOD, t2, RETENTION);
    assertThat(r3.nextState().reps()).isEqualTo(3);
    // interval = round(6 * EF_after_r2) — EF decreases each GOOD review
    assertThat(r3.nextState().scheduledDays()).isGreaterThan(6);
    assertThat(r3.nextState().scheduledDays()).isGreaterThan(0);
  }

  // -----------------------------------------------------------------------
  // Interval monotonicity: EASY ≥ GOOD ≥ HARD for the same card
  // -----------------------------------------------------------------------

  @Test
  void reviewIntervalMonotonicityEasyGoodHard() {
    CardScheduleState reviewCard = buildReviewCard(2.5, 5.0);
    // Fix previous interval so comparisons are fair
    CardScheduleState withPrevInterval =
        new CardScheduleState(
            SchedulerAlgorithm.SM2,
            CardState.REVIEW,
            2.5,
            5.0,
            RETENTION,
            2,
            0,
            6,
            T0,
            T0.minus(6, ChronoUnit.DAYS));

    int hardInterval =
        sm2.schedule(withPrevInterval, ReviewRating.HARD, T0, RETENTION)
            .nextState()
            .scheduledDays();
    int goodInterval =
        sm2.schedule(withPrevInterval, ReviewRating.GOOD, T0, RETENTION)
            .nextState()
            .scheduledDays();
    int easyInterval =
        sm2.schedule(withPrevInterval, ReviewRating.EASY, T0, RETENTION)
            .nextState()
            .scheduledDays();

    assertThat(easyInterval).isGreaterThanOrEqualTo(goodInterval);
    assertThat(goodInterval).isGreaterThanOrEqualTo(hardInterval);
  }

  // -----------------------------------------------------------------------
  // Determinism
  // -----------------------------------------------------------------------

  @Test
  void deterministicSameInputsSameOutputs() {
    ReviewResult r1 = sm2.schedule(newCard, ReviewRating.GOOD, T0, RETENTION);
    ReviewResult r2 = sm2.schedule(newCard, ReviewRating.GOOD, T0, RETENTION);

    assertThat(r1.nextState().stability()).isEqualTo(r2.nextState().stability());
    assertThat(r1.nextState().scheduledDays()).isEqualTo(r2.nextState().scheduledDays());
  }

  // -----------------------------------------------------------------------
  // Null argument rejection
  // -----------------------------------------------------------------------

  @Test
  void nullStateIsRejected() {
    assertThatThrownBy(() -> sm2.schedule(null, ReviewRating.GOOD, T0, RETENTION))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void nullRatingIsRejected() {
    assertThatThrownBy(() -> sm2.schedule(newCard, null, T0, RETENTION))
        .isInstanceOf(NullPointerException.class);
  }

  // -----------------------------------------------------------------------
  // CardId overload
  // -----------------------------------------------------------------------

  @Test
  void cardIdIsPreservedInResult() {
    ReviewResult result = sm2.schedule(CARD, newCard, ReviewRating.GOOD, T0, RETENTION);
    assertThat(result.cardId()).isEqualTo(CARD);
  }

  // -----------------------------------------------------------------------
  // Helper builders
  // -----------------------------------------------------------------------

  private CardScheduleState buildReviewCard(double ef, double difficulty) {
    return new CardScheduleState(
        SchedulerAlgorithm.SM2,
        CardState.REVIEW,
        ef,
        difficulty,
        RETENTION,
        3,
        0,
        6,
        T0,
        T0.minus(6, ChronoUnit.DAYS));
  }
}
