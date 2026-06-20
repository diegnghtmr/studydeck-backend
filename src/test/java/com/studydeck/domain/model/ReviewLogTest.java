package com.studydeck.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReviewLogTest {

  private static final CardId CARD_ID = new CardId(UUID.randomUUID());
  private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

  @Test
  void factoryCreatesValidReviewLog() {
    ReviewLog log = ReviewLog.of(CARD_ID, ReviewRating.GOOD, CardState.NEW, NOW, 0, 1);

    assertThat(log.cardId()).isEqualTo(CARD_ID);
    assertThat(log.rating()).isEqualTo(ReviewRating.GOOD);
    assertThat(log.stateBefore()).isEqualTo(CardState.NEW);
    assertThat(log.reviewedAt()).isEqualTo(NOW);
    assertThat(log.elapsedDays()).isEqualTo(0);
    assertThat(log.scheduledDays()).isEqualTo(1);
    assertThat(log.responseTimeMs()).isNull();
  }

  @Test
  void fullConstructorStoresResponseTime() {
    ReviewLog log = new ReviewLog(CARD_ID, ReviewRating.EASY, CardState.REVIEW, NOW, 3, 10, 1500);

    assertThat(log.responseTimeMs()).isEqualTo(1500);
  }

  @Test
  void nullCardIdIsRejected() {
    assertThatThrownBy(() -> new ReviewLog(null, ReviewRating.GOOD, CardState.NEW, NOW, 0, 1, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("cardId");
  }

  @Test
  void nullRatingIsRejected() {
    assertThatThrownBy(() -> new ReviewLog(CARD_ID, null, CardState.NEW, NOW, 0, 1, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("rating");
  }

  @Test
  void negativeElapsedDaysIsRejected() {
    assertThatThrownBy(
            () -> new ReviewLog(CARD_ID, ReviewRating.GOOD, CardState.NEW, NOW, -1, 1, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("elapsedDays");
  }

  @Test
  void negativeScheduledDaysIsRejected() {
    assertThatThrownBy(
            () -> new ReviewLog(CARD_ID, ReviewRating.GOOD, CardState.NEW, NOW, 0, -1, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("scheduledDays");
  }

  @Test
  void negativeResponseTimeMsIsRejected() {
    assertThatThrownBy(
            () -> new ReviewLog(CARD_ID, ReviewRating.GOOD, CardState.NEW, NOW, 0, 1, -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("responseTimeMs");
  }
}
