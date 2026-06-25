package com.studydeck.infrastructure.adapter.in.web;

import com.studydeck.domain.model.OwnerId;
import com.studydeck.domain.port.in.GetUserStatsQuery;
import com.studydeck.infrastructure.adapter.in.web.dto.UserStatsResponse;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Driving adapter — REST controller for user-scoped statistics.
 *
 * <p>Depends exclusively on the {@link GetUserStatsQuery} input port.
 */
@RestController
@RequestMapping("/v1/stats")
class StatsController {

  private final GetUserStatsQuery getUserStats;

  StatsController(@Qualifier("getUserStatsQuery") GetUserStatsQuery getUserStats) {
    this.getUserStats = getUserStats;
  }

  /**
   * GET /v1/stats — returns cross-deck statistics for the authenticated user.
   *
   * @param tz IANA timezone id; defaults to UTC if absent or invalid
   */
  @GetMapping
  ResponseEntity<UserStatsResponse> getUserStats(
      @RequestParam(required = false) String tz, @AuthenticationPrincipal Jwt jwt) {
    OwnerId ownerId = new OwnerId(UUID.fromString(jwt.getSubject()));
    ZoneId zone = parseZone(tz);
    Instant now = Instant.now();

    GetUserStatsQuery.UserStatsResult stats =
        getUserStats.execute(new GetUserStatsQuery.Query(ownerId, zone, now));

    UserStatsResponse response =
        new UserStatsResponse(
            stats.dueToday(),
            stats.newCards(),
            stats.reviewedToday(),
            stats.dayStreak(),
            stats.retention30d(),
            stats.dailyGoal(),
            stats.desiredRetention(),
            stats.newCardsPerDay(),
            stats.language(),
            stats.timezone(),
            stats.schedulerAlgorithm() != null ? stats.schedulerAlgorithm().name() : null);
    return ResponseEntity.ok(response);
  }

  private static ZoneId parseZone(String tz) {
    if (tz == null || tz.isBlank()) {
      return ZoneId.of("UTC");
    }
    try {
      return ZoneId.of(tz);
    } catch (java.time.zone.ZoneRulesException e) {
      return ZoneId.of("UTC");
    }
  }
}
