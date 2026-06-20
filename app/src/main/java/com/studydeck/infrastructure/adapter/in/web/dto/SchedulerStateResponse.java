package com.studydeck.infrastructure.adapter.in.web.dto;

import java.time.Instant;

/** Mirrors the OpenAPI SchedulerState schema. */
public record SchedulerStateResponse(
    Instant dueAt,
    Double stability,
    Double difficulty,
    Double retrievability,
    Integer elapsedDays,
    Integer scheduledDays,
    Double desiredRetention) {}
