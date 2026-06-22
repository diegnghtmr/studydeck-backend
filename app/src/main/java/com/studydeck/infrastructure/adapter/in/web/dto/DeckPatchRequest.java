package com.studydeck.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request DTO for patching a Deck, matching the OpenAPI DeckPatchRequest schema.
 *
 * <p>All fields are optional — only non-null fields are applied.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record DeckPatchRequest(
    @Size(min = 1, max = 120) String title,
    @Size(max = 1000) String description,
    Boolean archived,
    List<String> tags,
    @DecimalMin("0.7") @DecimalMax("0.99") Double defaultDesiredRetention,
    @Size(max = 40) String icon,
    @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String color) {}
