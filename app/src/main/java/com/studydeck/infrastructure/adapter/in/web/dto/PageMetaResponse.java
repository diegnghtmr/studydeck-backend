package com.studydeck.infrastructure.adapter.in.web.dto;

/**
 * Pagination metadata envelope matching the OpenAPI PageMeta schema.
 *
 * <p>Fields: page, size, totalElements, totalPages, hasNext, hasPrevious.
 */
public record PageMetaResponse(
    int page,
    int size,
    long totalElements,
    long totalPages,
    boolean hasNext,
    boolean hasPrevious) {}
