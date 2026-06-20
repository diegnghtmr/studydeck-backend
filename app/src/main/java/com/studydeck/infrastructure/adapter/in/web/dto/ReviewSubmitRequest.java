package com.studydeck.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/** Request body for POST /v1/reviews. */
public record ReviewSubmitRequest(
    UUID sessionId,
    @NotNull UUID cardId,
    @NotNull String rating,
    Integer responseTimeMs,
    Boolean revealedAnswer,
    Instant clientPresentAt) {}
