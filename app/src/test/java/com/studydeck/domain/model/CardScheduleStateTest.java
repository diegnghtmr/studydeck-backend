package com.studydeck.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class CardScheduleStateTest {

  private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

  // -----------------------------------------------------------------------
  // Factory methods
  // -----------------------------------------------------------------------

  @Test
  void newFsrsCardHasExpectedDefaults() {
    CardScheduleState state = CardScheduleState.newFsrsCard(NOW);

    assertThat(state.algorithm()).isEqualTo(SchedulerAlgorithm.FSRS);
    assertThat(state.state()).isEqualTo(CardState.NEW);
    assertThat(state.stability()).isEqualTo(0.0);
    assertThat(state.difficulty()).isEqualTo(5.0);
    assertThat(state.desiredRetention()).isEqualTo(0.9);
    assertThat(state.reps()).isEqualTo(0);
    assertThat(state.lapses()).isEqualTo(0);
    assertThat(state.scheduledDays()).isEqualTo(0);
    assertThat(state.dueAt()).isEqualTo(NOW);
    assertThat(state.lastReviewedAt()).isNull();
  }

  @Test
  void newCardFactoryWithExplicitAlgorithmAndRetention() {
    CardScheduleState state = CardScheduleState.newCard(SchedulerAlgorithm.SM2, 0.85, NOW);

    assertThat(state.algorithm()).isEqualTo(SchedulerAlgorithm.SM2);
    assertThat(state.desiredRetention()).isEqualTo(0.85);
    assertThat(state.state()).isEqualTo(CardState.NEW);
  }

  // -----------------------------------------------------------------------
  // Invariant validation
  // -----------------------------------------------------------------------

  @Test
  void nullAlgorithmIsRejected() {
    assertThatThrownBy(
            () -> new CardScheduleState(null, CardState.NEW, 0.0, 5.0, 0.9, 0, 0, 0, NOW, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("algorithm");
  }

  @Test
  void nullStateIsRejected() {
    assertThatThrownBy(
            () ->
                new CardScheduleState(
                    SchedulerAlgorithm.FSRS, null, 0.0, 5.0, 0.9, 0, 0, 0, NOW, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("state");
  }

  @Test
  void nullDueAtIsRejected() {
    assertThatThrownBy(
            () ->
                new CardScheduleState(
                    SchedulerAlgorithm.FSRS, CardState.NEW, 0.0, 5.0, 0.9, 0, 0, 0, null, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("dueAt");
  }

  @Test
  void negativeStabilityIsRejected() {
    assertThatThrownBy(
            () ->
                new CardScheduleState(
                    SchedulerAlgorithm.FSRS, CardState.NEW, -0.1, 5.0, 0.9, 0, 0, 0, NOW, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("stability");
  }

  @Test
  void difficultyAbove10IsRejected() {
    assertThatThrownBy(
            () ->
                new CardScheduleState(
                    SchedulerAlgorithm.FSRS, CardState.NEW, 1.0, 10.1, 0.9, 0, 0, 0, NOW, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("difficulty");
  }

  @Test
  void negativeRepsIsRejected() {
    assertThatThrownBy(
            () ->
                new CardScheduleState(
                    SchedulerAlgorithm.FSRS, CardState.NEW, 1.0, 5.0, 0.9, -1, 0, 0, NOW, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reps");
  }

  @Test
  void negativeLapsesIsRejected() {
    assertThatThrownBy(
            () ->
                new CardScheduleState(
                    SchedulerAlgorithm.FSRS, CardState.NEW, 1.0, 5.0, 0.9, 0, -1, 0, NOW, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("lapses");
  }

  @Test
  void retentionBelowMinIsRejected() {
    assertThatThrownBy(
            () ->
                new CardScheduleState(
                    SchedulerAlgorithm.FSRS, CardState.NEW, 0.0, 5.0, 0.69, 0, 0, 0, NOW, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("desiredRetention");
  }

  @Test
  void retentionAboveMaxIsRejected() {
    assertThatThrownBy(
            () ->
                new CardScheduleState(
                    SchedulerAlgorithm.FSRS, CardState.NEW, 0.0, 5.0, 1.0, 0, 0, 0, NOW, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("desiredRetention");
  }

  @Test
  void retentionBoundaryValuesAreAccepted() {
    assertThat(
            new CardScheduleState(
                    SchedulerAlgorithm.FSRS, CardState.NEW, 0.0, 5.0, 0.70, 0, 0, 0, NOW, null)
                .desiredRetention())
        .isEqualTo(0.70);
    assertThat(
            new CardScheduleState(
                    SchedulerAlgorithm.FSRS, CardState.NEW, 0.0, 5.0, 0.99, 0, 0, 0, NOW, null)
                .desiredRetention())
        .isEqualTo(0.99);
  }

  // -----------------------------------------------------------------------
  // withState
  // -----------------------------------------------------------------------

  @Test
  void withStateProducesNewRecordWithUpdatedFields() {
    CardScheduleState initial = CardScheduleState.newFsrsCard(NOW);
    Instant dueAt = NOW.plusSeconds(86400);

    CardScheduleState updated =
        initial.withState(CardState.LEARNING, 1.5, 5.2, 1, 0, 1, dueAt, NOW);

    assertThat(updated.state()).isEqualTo(CardState.LEARNING);
    assertThat(updated.stability()).isEqualTo(1.5);
    assertThat(updated.difficulty()).isEqualTo(5.2);
    assertThat(updated.reps()).isEqualTo(1);
    assertThat(updated.lapses()).isEqualTo(0);
    assertThat(updated.scheduledDays()).isEqualTo(1);
    assertThat(updated.dueAt()).isEqualTo(dueAt);
    assertThat(updated.lastReviewedAt()).isEqualTo(NOW);
    // algorithm and desiredRetention unchanged
    assertThat(updated.algorithm()).isEqualTo(SchedulerAlgorithm.FSRS);
    assertThat(updated.desiredRetention()).isEqualTo(0.9);
  }
}
