package com.studydeck.infrastructure.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * REST response DTO for a Card, matching the OpenAPI Card schema.
 *
 * <p>Maps domain Card.ordinal → position, Card.cardVariant → cardVariant. noteType is kebab-case.
 * deckId is carried from the parent note's deck (enriched by the persistence layer). dueAt and
 * schedulerState are null in P1 (no FSRS yet).
 */
public record CardResponse(
    UUID id,
    UUID noteId,
    UUID deckId,
    NoteTypeValue noteType,
    String cardVariant,
    int position,
    boolean suspended,
    Instant dueAt,
    Instant createdAt,
    Instant updatedAt) {}
