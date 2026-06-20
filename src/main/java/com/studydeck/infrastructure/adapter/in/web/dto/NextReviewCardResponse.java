package com.studydeck.infrastructure.adapter.in.web.dto;

import java.util.UUID;

/** Response for GET /v1/review-sessions/{id}/next — mirrors the OpenAPI NextReviewCard schema. */
public record NextReviewCardResponse(UUID sessionId, CardResponse card) {}
