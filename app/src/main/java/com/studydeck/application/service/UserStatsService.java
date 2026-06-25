package com.studydeck.application.service;

import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.SchedulerAlgorithm;
import com.studydeck.domain.model.UserAccount;
import com.studydeck.domain.port.in.GetUserStatsQuery;
import com.studydeck.domain.port.out.CardScheduleStateRepository;
import com.studydeck.domain.port.out.ClockPort;
import com.studydeck.domain.port.out.ReviewLogRepository;
import com.studydeck.domain.port.out.UserAccountRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Application service implementing {@link GetUserStatsQuery}.
 *
 * <p>Framework-free: no Spring annotations. Wired as {@code @Bean} in {@code BeanConfiguration}.
 *
 * <p>Streak convention: a streak counts consecutive days (in the user's timezone) going back from
 * today on which the user performed at least one review. If today has no review, the streak is 0
 * (today breaks the chain).
 *
 * <p>Timezone resolution: the user's stored timezone takes precedence over the query param zone
 * when the stored timezone differs from UTC.
 */
public final class UserStatsService implements GetUserStatsQuery {

  private final CardScheduleStateRepository scheduleStateRepository;
  private final ReviewLogRepository reviewLogRepository;
  private final UserAccountRepository userAccountRepository;
  private final ClockPort clockPort;

  public UserStatsService(
      CardScheduleStateRepository scheduleStateRepository,
      ReviewLogRepository reviewLogRepository,
      UserAccountRepository userAccountRepository,
      ClockPort clockPort) {
    this.scheduleStateRepository = scheduleStateRepository;
    this.reviewLogRepository = reviewLogRepository;
    this.userAccountRepository = userAccountRepository;
    this.clockPort = clockPort;
  }

  @Override
  public UserStatsResult execute(Query query) {
    OwnerId ownerId = query.ownerId();
    ZoneId queryZone = query.zone();
    Instant now = query.now();

    Optional<UserAccount> accountOpt = userAccountRepository.findById(ownerId);

    int dailyGoal = accountOpt.map(UserAccount::getDailyGoal).orElse(40);
    double desiredRetention = accountOpt.map(UserAccount::getDesiredRetention).orElse(0.90);
    int newCardsPerDay = accountOpt.map(UserAccount::getNewCardsPerDay).orElse(10);
    String language = accountOpt.map(UserAccount::getLanguage).orElse("en");
    String storedTimezone = accountOpt.map(UserAccount::getTimezone).orElse("UTC");
    SchedulerAlgorithm schedulerAlgorithm =
        accountOpt.map(UserAccount::getSchedulerAlgorithm).orElse(SchedulerAlgorithm.FSRS);

    // Prefer the user's stored timezone when it differs from UTC
    ZoneId effectiveZone = !"UTC".equals(storedTimezone) ? ZoneId.of(storedTimezone) : queryZone;

    long dueToday = scheduleStateRepository.countDueGlobal(ownerId, now);
    long newCards = scheduleStateRepository.countNewGlobal(ownerId);

    LocalDate today = now.atZone(effectiveZone).toLocalDate();
    Instant dayStart = today.atStartOfDay(effectiveZone).toInstant();
    Instant dayEnd = today.plusDays(1).atStartOfDay(effectiveZone).toInstant();
    long reviewedToday = reviewLogRepository.countReviewedTodayGlobal(ownerId, dayStart, dayEnd);

    int dayStreak = computeStreak(ownerId, effectiveZone, today);

    Instant thirtyDaysAgo = now.minus(Duration.ofDays(30));
    Double retention30d = reviewLogRepository.averageRetentionGlobal(ownerId, thirtyDaysAgo);

    return new UserStatsResult(
        dueToday,
        newCards,
        reviewedToday,
        dayStreak,
        retention30d,
        dailyGoal,
        desiredRetention,
        newCardsPerDay,
        language,
        storedTimezone,
        schedulerAlgorithm);
  }

  private int computeStreak(OwnerId ownerId, ZoneId zone, LocalDate today) {
    List<LocalDate> reviewDays = reviewLogRepository.distinctReviewDays(ownerId, zone);
    if (reviewDays.isEmpty() || !reviewDays.get(0).equals(today)) {
      return 0;
    }
    int streak = 0;
    LocalDate expected = today;
    for (LocalDate day : reviewDays) {
      if (day.equals(expected)) {
        streak++;
        expected = expected.minusDays(1);
      } else {
        break;
      }
    }
    return streak;
  }
}
