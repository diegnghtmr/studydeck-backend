package com.studydeck.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReviewRatingTest {

  @Test
  void allFourValuesArePresent() {
    assertThat(ReviewRating.values())
        .containsExactlyInAnyOrder(
            ReviewRating.AGAIN, ReviewRating.HARD, ReviewRating.GOOD, ReviewRating.EASY);
  }

  @Test
  void ordinalOrderIsAgainHardGoodEasy() {
    assertThat(ReviewRating.AGAIN.ordinal()).isLessThan(ReviewRating.HARD.ordinal());
    assertThat(ReviewRating.HARD.ordinal()).isLessThan(ReviewRating.GOOD.ordinal());
    assertThat(ReviewRating.GOOD.ordinal()).isLessThan(ReviewRating.EASY.ordinal());
  }

  @Test
  void valueOfMatchesName() {
    assertThat(ReviewRating.valueOf("AGAIN")).isEqualTo(ReviewRating.AGAIN);
    assertThat(ReviewRating.valueOf("HARD")).isEqualTo(ReviewRating.HARD);
    assertThat(ReviewRating.valueOf("GOOD")).isEqualTo(ReviewRating.GOOD);
    assertThat(ReviewRating.valueOf("EASY")).isEqualTo(ReviewRating.EASY);
  }
}
