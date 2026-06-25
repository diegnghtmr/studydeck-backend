package com.studydeck.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Response DTO for GET /v1/stats — user-scoped cross-deck statistics. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserStatsResponse(
    long dueToday,
    long newCards,
    long reviewedToday,
    int dayStreak,
    Double retention30d,
    int dailyGoal,
    double desiredRetention,
    int newCardsPerDay,
    String language,
    String timezone) {}
