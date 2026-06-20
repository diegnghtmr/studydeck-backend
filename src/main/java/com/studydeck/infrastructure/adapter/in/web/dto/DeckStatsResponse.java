package com.studydeck.infrastructure.adapter.in.web.dto;

import java.util.UUID;

/** Response for GET /v1/decks/{deckId}/stats — mirrors the OpenAPI DeckStats schema. */
public record DeckStatsResponse(
    UUID deckId,
    int totalNotes,
    int totalCards,
    int dueToday,
    int reviewedToday,
    int suspendedCards,
    Double againRate7d,
    Double averageRetention30d) {}
