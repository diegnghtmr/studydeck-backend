package com.studydeck.infrastructure.adapter.in.web.dto;

import java.util.List;

/**
 * REST response for a non-paginated list of cards.
 *
 * <p>Shape: { "items": [...] } — matches the /v1/notes/{noteId}/cards response schema.
 */
public record CardListResponse(List<CardResponse> items) {}
