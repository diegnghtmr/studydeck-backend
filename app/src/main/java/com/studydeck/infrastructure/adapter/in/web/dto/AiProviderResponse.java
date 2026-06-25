package com.studydeck.infrastructure.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for an AI provider configuration.
 *
 * <p>Contains metadata and a masked key hint. The plaintext {@code apiKey} is NEVER returned.
 */
public record AiProviderResponse(
    UUID id,
    String label,
    String baseUrl,
    String model,
    String keyHint,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {}
