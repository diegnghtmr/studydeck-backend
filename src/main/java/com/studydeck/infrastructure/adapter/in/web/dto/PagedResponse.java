package com.studydeck.infrastructure.adapter.in.web.dto;

import java.util.List;

/**
 * Generic paginated list envelope matching the OpenAPI PagedXxx schema pattern.
 *
 * <p>Shape: { "items": [...], "page": { PageMeta } }
 *
 * @param <T> the element type
 */
public record PagedResponse<T>(List<T> items, PageMetaResponse page) {}
