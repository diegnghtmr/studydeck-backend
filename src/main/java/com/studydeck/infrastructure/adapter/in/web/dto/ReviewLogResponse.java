package com.studydeck.infrastructure.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;

/** Single entry in GET /v1/reviews/history — mirrors the OpenAPI ReviewLog schema. */
public record ReviewLogResponse(
    UUID cardId,
    String rating,
    String stateBefore,
    Instant reviewedAt,
    int elapsedDays,
    int scheduledDays,
    Integer responseTimeMs) {}
