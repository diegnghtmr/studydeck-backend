package com.studydeck.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for updating an existing AI provider configuration.
 *
 * <p>The {@code apiKey} is optional on update: if {@code null}, the existing encrypted key is
 * preserved. It is write-only and never returned in any response.
 */
public record AiProviderUpdateRequest(
    @NotBlank(message = "label must not be blank") String label,
    @NotBlank(message = "baseUrl must not be blank") String baseUrl,
    @NotBlank(message = "model must not be blank") String model,
    String apiKey) {}
