package com.studydeck.infrastructure.adapter.in.web.dto;

import java.util.UUID;

/** Request body for POST /v1/review-sessions. */
public record ReviewSessionCreateRequest(UUID deckId, Integer maxCards, Boolean includeLearning) {}
