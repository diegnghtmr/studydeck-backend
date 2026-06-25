package com.studydeck.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for creating a new AI provider configuration.
 *
 * <p>The {@code apiKey} is write-only: it is accepted here but never returned in any response.
 */
public record AiProviderCreateRequest(
    @NotBlank(message = "label must not be blank") String label,
    @NotBlank(message = "baseUrl must not be blank") String baseUrl,
    @NotBlank(message = "model must not be blank") String model,
    @NotBlank(message = "apiKey must not be blank") String apiKey) {}
