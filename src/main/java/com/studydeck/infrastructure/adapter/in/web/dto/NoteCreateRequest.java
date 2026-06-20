package com.studydeck.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * Request DTO for creating a Note, matching the OpenAPI NoteCreateRequest schema.
 *
 * <p>The noteType field acts as the discriminator for the content oneOf. Content is deserialized as
 * a raw JsonNode and resolved to the correct NoteContentDto subtype by the web layer using noteType
 * as the discriminator (since BasicNoteContent and ReversedNoteContent share the same JSON field
 * shape and cannot be distinguished by field-presence deduction alone).
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record NoteCreateRequest(
    @NotNull UUID deckId,
    @NotNull NoteTypeValue noteType,
    List<String> tags,
    @NotNull JsonNode content) {}
