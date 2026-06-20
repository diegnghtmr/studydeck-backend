package com.studydeck.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * Request DTO for patching a Note, matching the OpenAPI NotePatchRequest schema.
 *
 * <p>All fields are optional. Content is deserialized as a raw JsonNode so the controller can use
 * the existing note's noteType as the discriminator when converting to NoteContentDto.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record NotePatchRequest(List<String> tags, JsonNode content) {}
