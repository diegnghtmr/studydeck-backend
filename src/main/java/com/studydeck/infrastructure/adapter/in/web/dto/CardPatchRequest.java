package com.studydeck.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/**
 * Request DTO for patching a Card, matching the OpenAPI CardPatchRequest schema.
 *
 * <p>All fields are optional. suspended controls card suspension state. dueAt is reserved for
 * future FSRS integration.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record CardPatchRequest(Boolean suspended, Instant dueAt) {}
