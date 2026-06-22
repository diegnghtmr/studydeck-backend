package com.studydeck.application.service;

import com.studydeck.domain.model.OwnerId;
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

/**
 * Application service implementing {@link GetUserStatsQuery}.
 *
 * <p>Framework-free: no Spring annotations. Wired as {@code @Bean} in {@code BeanConfiguration}.
 *
 * <p>Streak convention: a streak counts consecutive days (in the user's timezone) going back from
 * today on which the user performed at least one review. If today has no review, the streak is 0
 * (today breaks the chain).
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
    ZoneId zone = query.zone();
    Instant now = query.now();

    long dueToday = scheduleStateRepository.countDueGlobal(ownerId, now);
    long newCards = scheduleStateRepository.countNewGlobal(ownerId);

    LocalDate today = now.atZone(zone).toLocalDate();
    Instant dayStart = today.atStartOfDay(zone).toInstant();
    Instant dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant();
    long reviewedToday = reviewLogRepository.countReviewedTodayGlobal(ownerId, dayStart, dayEnd);

    int dayStreak = computeStreak(ownerId, zone, today);

    Instant thirtyDaysAgo = now.minus(Duration.ofDays(30));
    Double retention30d = reviewLogRepository.averageRetentionGlobal(ownerId, thirtyDaysAgo);

    int dailyGoal = userAccountRepository.findById(ownerId).map(u -> u.getDailyGoal()).orElse(40);

    return new UserStatsResult(
        dueToday, newCards, reviewedToday, dayStreak, retention30d, dailyGoal);
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
