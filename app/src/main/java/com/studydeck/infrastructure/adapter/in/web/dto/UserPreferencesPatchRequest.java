package com.studydeck.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request DTO for PATCH /v1/account/preferences. All fields are optional (partial update). */
@JsonIgnoreProperties(ignoreUnknown = false)
public record UserPreferencesPatchRequest(
    @Min(1) @Max(1000) Integer dailyGoal,
    @DecimalMin("0.50") @DecimalMax("0.99") Double desiredRetention,
    @Min(0) @Max(999) Integer newCardsPerDay,
    @Pattern(regexp = "^(en|es|fr|pt)$") String language,
    @Size(min = 1, max = 64) String timezone) {}
