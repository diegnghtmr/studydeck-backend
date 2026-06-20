package com.studydeck.infrastructure.adapter.in.web.dto;

import java.util.List;
import java.util.UUID;

/** Response DTO for POST /v1/imports/flashcards (matches OpenAPI ImportResult schema). */
public record ImportResultResponse(
    UUID importId,
    UUID deckId,
    int importedNotes,
    int importedCards,
    int duplicateNotes,
    int rejectedNotes,
    List<String> warnings) {}
