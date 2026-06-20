package com.studydeck.infrastructure.adapter.in.web.dto;

import java.util.List;

/**
 * REST response for the NoteType list endpoint.
 *
 * <p>Shape: { "items": [...] } — matches the OpenAPI /v1/note-types response schema.
 */
public record NoteTypeListResponse(List<NoteTypeDescriptorResponse> items) {}
