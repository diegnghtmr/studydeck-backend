package com.studydeck.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.studydeck.application.support.FixedClockPort;
import com.studydeck.application.support.InMemoryCardScheduleStateRepository;
import com.studydeck.application.support.InMemoryReviewLogRepository;
import com.studydeck.application.support.InMemoryUserAccountRepository;
import com.studydeck.domain.model.CardId;
import com.studydeck.domain.model.CardScheduleState;
import com.studydeck.domain.model.CardState;
import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.ReviewLog;
import com.studydeck.domain.model.ReviewRating;
import com.studydeck.domain.model.SchedulerAlgorithm;
import com.studydeck.domain.port.in.GetUserStatsQuery;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Application-layer unit tests for {@link UserStatsService}.
 *
 * <p>Pure Java — no Spring context. Uses in-memory stubs for all output ports.
 */
class UserStatsServiceTest {

  private InMemoryCardScheduleStateRepository scheduleRepo;
  private InMemoryReviewLogRepository logRepo;
  private InMemoryUserAccountRepository userRepo;
  private FixedClockPort clock;
  private UserStatsService sut;

  private final OwnerId alice = OwnerId.generate();
  private static final ZoneId UTC = ZoneId.of("UTC");
  private static final ZoneId BA_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

  // Fixed "now" = 2026-01-15T14:00:00Z (2pm UTC, so 11am in Buenos Aires -3h)
  private static final Instant NOW = Instant.parse("2026-01-15T14:00:00Z");

  @BeforeEach
  void setUp() {
    scheduleRepo = new InMemoryCardScheduleStateRepository();
    logRepo = new InMemoryReviewLogRepository();
    userRepo = new InMemoryUserAccountRepository();
    clock = FixedClockPort.at(NOW);
    sut = new UserStatsService(scheduleRepo, logRepo, userRepo, clock);
  }

  private CardScheduleState dueState() {
    return new CardScheduleState(
        SchedulerAlgorithm.FSRS,
        CardState.REVIEW,
        5.0,
        5.0,
        0.9,
        1,
        0,
        5,
        NOW.minusSeconds(3600),
        NOW.minusSeconds(86400));
  }

  private CardScheduleState newState() {
    return CardScheduleState.newFsrsCard(NOW);
  }

  private CardScheduleState futureState() {
    return new CardScheduleState(
        SchedulerAlgorithm.FSRS,
        CardState.REVIEW,
        5.0,
        5.0,
        0.9,
        1,
        0,
        10,
        NOW.plusSeconds(86400 * 5),
        NOW.minusSeconds(86400));
  }

  private void saveReviewAt(Instant reviewedAt, ReviewRating rating) {
    ReviewLog log =
        new ReviewLog(CardId.generate(), rating, CardState.REVIEW, reviewedAt, 1, 5, null);
    logRepo.save(alice, null, log);
  }

  @Nested
  @DisplayName("Zero data")
  class ZeroData {

    @Test
    @DisplayName("All stats are zero/null when user has no data")
    void allZeroWhenNoData() {
      GetUserStatsQuery.UserStatsResult result =
          sut.execute(new GetUserStatsQuery.Query(alice, UTC, NOW));

      assertThat(result.dueToday()).isEqualTo(0);
      assertThat(result.newCards()).isEqualTo(0);
      assertThat(result.reviewedToday()).isEqualTo(0);
      assertThat(result.dayStreak()).isEqualTo(0);
      assertThat(result.retention30d()).isNull();
    }
  }

  @Nested
  @DisplayName("dueToday")
  class DueTodayTests {

    @Test
    @DisplayName("Counts only cards whose due_at <= now")
    void countsDueCards() {
      scheduleRepo.save(alice, CardId.generate(), dueState());
      scheduleRepo.save(alice, CardId.generate(), futureState());

      GetUserStatsQuery.UserStatsResult result =
          sut.execute(new GetUserStatsQuery.Query(alice, UTC, NOW));

      assertThat(result.dueToday()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("newCards")
  class NewCardsTests {

    @Test
    @DisplayName("Counts cards in NEW state")
    void countsNewStateCards() {
      scheduleRepo.save(alice, CardId.generate(), newState());
      scheduleRepo.save(alice, CardId.generate(), dueState()); // REVIEW, not new

      GetUserStatsQuery.UserStatsResult result =
          sut.execute(new GetUserStatsQuery.Query(alice, UTC, NOW));

      assertThat(result.newCards()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("reviewedToday")
  class ReviewedTodayTests {

    @Test
    @DisplayName("Counts reviews within today's window in UTC")
    void countsReviewsInTodayWindow() {
      saveReviewAt(Instant.parse("2026-01-15T08:00:00Z"), ReviewRating.GOOD);
      saveReviewAt(Instant.parse("2026-01-15T12:00:00Z"), ReviewRating.GOOD);
      saveReviewAt(Instant.parse("2026-01-14T23:59:59Z"), ReviewRating.GOOD); // yesterday

      GetUserStatsQuery.UserStatsResult result =
          sut.execute(new GetUserStatsQuery.Query(alice, UTC, NOW));

      assertThat(result.reviewedToday()).isEqualTo(2);
    }

    @Test
    @DisplayName("Timezone affects today boundary — Buenos Aires is UTC-3")
    void timezoneAffectsTodayBoundary() {
      // In Buenos Aires (UTC-3), 2026-01-15 starts at 2026-01-15T03:00:00Z
      saveReviewAt(Instant.parse("2026-01-15T02:30:00Z"), ReviewRating.GOOD); // BA: Jan 14
      saveReviewAt(Instant.parse("2026-01-15T03:30:00Z"), ReviewRating.GOOD); // BA: Jan 15

      GetUserStatsQuery.UserStatsResult result =
          sut.execute(new GetUserStatsQuery.Query(alice, BA_ZONE, NOW));

      assertThat(result.reviewedToday()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("dayStreak")
  class DayStreakTests {

    @Test
    @DisplayName("Streak = 0 when no review today")
    void streakZeroWhenNoReviewToday() {
      saveReviewAt(Instant.parse("2026-01-14T10:00:00Z"), ReviewRating.GOOD);

      GetUserStatsQuery.UserStatsResult result =
          sut.execute(new GetUserStatsQuery.Query(alice, UTC, NOW));

      assertThat(result.dayStreak()).isEqualTo(0);
    }

    @Test
    @DisplayName("Streak = 1 when only today has a review")
    void streakOneWhenOnlyToday() {
      saveReviewAt(Instant.parse("2026-01-15T10:00:00Z"), ReviewRating.GOOD);

      GetUserStatsQuery.UserStatsResult result =
          sut.execute(new GetUserStatsQuery.Query(alice, UTC, NOW));

      assertThat(result.dayStreak()).isEqualTo(1);
    }

    @Test
    @DisplayName("Streak = 3 for today + 2 consecutive days back")
    void streakThreeConsecutiveDays() {
      saveReviewAt(Instant.parse("2026-01-15T10:00:00Z"), ReviewRating.GOOD); // today
      saveReviewAt(Instant.parse("2026-01-14T10:00:00Z"), ReviewRating.GOOD); // yesterday
      saveReviewAt(Instant.parse("2026-01-13T10:00:00Z"), ReviewRating.GOOD); // day before

      GetUserStatsQuery.UserStatsResult result =
          sut.execute(new GetUserStatsQuery.Query(alice, UTC, NOW));

      assertThat(result.dayStreak()).isEqualTo(3);
    }

    @Test
    @DisplayName("Streak breaks at gap — today + 2 days ago but not yesterday")
    void streakBreaksAtGap() {
      saveReviewAt(Instant.parse("2026-01-15T10:00:00Z"), ReviewRating.GOOD); // today
      // skip Jan 14
      saveReviewAt(
          Instant.parse("2026-01-13T10:00:00Z"), ReviewRating.GOOD); // day before yesterday

      GetUserStatsQuery.UserStatsResult result =
          sut.execute(new GetUserStatsQuery.Query(alice, UTC, NOW));

      assertThat(result.dayStreak()).isEqualTo(1);
    }

    @Test
    @DisplayName("Multiple reviews on same day count as one streak day")
    void multipleReviewsSameDay_countAsOne() {
      saveReviewAt(Instant.parse("2026-01-15T08:00:00Z"), ReviewRating.GOOD);
      saveReviewAt(Instant.parse("2026-01-15T12:00:00Z"), ReviewRating.AGAIN);
      saveReviewAt(Instant.parse("2026-01-14T09:00:00Z"), ReviewRating.GOOD);

      GetUserStatsQuery.UserStatsResult result =
          sut.execute(new GetUserStatsQuery.Query(alice, UTC, NOW));

      assertThat(result.dayStreak()).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("retention30d")
  class Retention30dTests {

    @Test
    @DisplayName("Returns null when no reviews in last 30 days")
    void nullWhenNoReviews() {
      saveReviewAt(NOW.minus(java.time.Duration.ofDays(31)), ReviewRating.GOOD);

      GetUserStatsQuery.UserStatsResult result =
          sut.execute(new GetUserStatsQuery.Query(alice, UTC, NOW));

      assertThat(result.retention30d()).isNull();
    }

    @Test
    @DisplayName("Returns 1.0 when all reviews are non-AGAIN")
    void oneWhenAllGood() {
      saveReviewAt(NOW.minus(java.time.Duration.ofDays(5)), ReviewRating.GOOD);
      saveReviewAt(NOW.minus(java.time.Duration.ofDays(3)), ReviewRating.EASY);

      GetUserStatsQuery.UserStatsResult result =
          sut.execute(new GetUserStatsQuery.Query(alice, UTC, NOW));

      assertThat(result.retention30d()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Returns 0.5 when half are AGAIN")
    void halfRetention() {
      saveReviewAt(NOW.minus(java.time.Duration.ofDays(5)), ReviewRating.AGAIN);
      saveReviewAt(NOW.minus(java.time.Duration.ofDays(3)), ReviewRating.GOOD);

      GetUserStatsQuery.UserStatsResult result =
          sut.execute(new GetUserStatsQuery.Query(alice, UTC, NOW));

      assertThat(result.retention30d()).isEqualTo(0.5);
    }
  }
}
