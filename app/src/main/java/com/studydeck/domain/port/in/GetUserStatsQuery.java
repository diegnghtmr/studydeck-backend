package com.studydeck.domain.port.in;

import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.model.SchedulerAlgorithm;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

/** Input port — user-scoped cross-deck statistics (GET /v1/stats). */
public interface GetUserStatsQuery {

  UserStatsResult execute(Query query);

  record Query(OwnerId ownerId, ZoneId zone, Instant now) {
    public Query {
      Objects.requireNonNull(ownerId, "ownerId must not be null");
      Objects.requireNonNull(zone, "zone must not be null");
      Objects.requireNonNull(now, "now must not be null");
    }
  }

  record UserStatsResult(
      long dueToday,
      long newCards,
      long reviewedToday,
      int dayStreak,
      Double retention30d,
      int dailyGoal,
      double desiredRetention,
      int newCardsPerDay,
      String language,
      String timezone,
      SchedulerAlgorithm schedulerAlgorithm) {}
}
