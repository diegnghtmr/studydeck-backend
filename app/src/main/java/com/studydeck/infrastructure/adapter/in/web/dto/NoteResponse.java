package com.studydeck.infrastructure.adapter.in.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST response DTO for a Note, matching the OpenAPI Note schema.
 *
 * <p>content is the typed note content DTO (oneOf). noteType serializes as kebab-case string.
 */
public record NoteResponse(
    UUID id,
    UUID deckId,
    NoteTypeValue noteType,
    List<String> tags,
    NoteContentDto content,
    Instant createdAt,
    Instant updatedAt) {}
