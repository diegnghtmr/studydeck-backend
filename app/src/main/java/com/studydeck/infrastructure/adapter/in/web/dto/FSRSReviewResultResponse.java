package com.studydeck.infrastructure.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;

/** Response for POST /v1/reviews — mirrors the OpenAPI FSRSReviewResult schema. */
public record FSRSReviewResultResponse(
    UUID cardId,
    UUID sessionId,
    String rating,
    Instant reviewedAt,
    SchedulerStateResponse previousState,
    SchedulerStateResponse nextState,
    UUID historyEntryId) {}
