package com.studydeck.infrastructure.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;

/** Response for GET /v1/review-sessions/{id} and POST /v1/review-sessions. */
public record ReviewSessionResponse(
    UUID id,
    UUID deckId,
    String status,
    Instant startedAt,
    Instant endedAt,
    Integer presentedCount,
    Integer answeredCount) {}
