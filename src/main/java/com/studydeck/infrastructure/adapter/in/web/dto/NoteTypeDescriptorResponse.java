package com.studydeck.infrastructure.adapter.in.web.dto;

import java.util.List;

/**
 * REST response DTO for a NoteType descriptor, matching the OpenAPI NoteTypeDescriptor schema.
 *
 * <p>name serializes as kebab-case (e.g. "multiple-choice"). fields is a list of field names.
 */
public record NoteTypeDescriptorResponse(
    NoteTypeValue name, String label, List<String> fields, String guidance) {}
