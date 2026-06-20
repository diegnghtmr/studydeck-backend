package com.studydeck.infrastructure.adapter.in.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST response DTO for a Deck, matching the OpenAPI Deck schema.
 *
 * <p>Fields: id, title, description, tags, archived, defaultDesiredRetention, createdAt, updatedAt.
 */
public record DeckResponse(
    UUID id,
    String title,
    String description,
    List<String> tags,
    boolean archived,
    double defaultDesiredRetention,
    Instant createdAt,
    Instant updatedAt) {}
