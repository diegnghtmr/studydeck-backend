package com.studydeck.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request DTO for creating a Deck, matching the OpenAPI DeckCreateRequest schema.
 *
 * <p>additionalProperties: false — enforced by {@code @JsonIgnoreProperties(failOnUnknownTokens =
 * true)} via ObjectMapper configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record DeckCreateRequest(
    @NotBlank @Size(min = 1, max = 120) String title,
    @Size(max = 1000) String description,
    @Size(max = 50) List<String> tags,
    @DecimalMin("0.7") @DecimalMax("0.99") Double defaultDesiredRetention,
    @Size(max = 40) String icon,
    @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String color) {}
