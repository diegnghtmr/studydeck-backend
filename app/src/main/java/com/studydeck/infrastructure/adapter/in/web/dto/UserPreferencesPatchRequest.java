package com.studydeck.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Request DTO for PATCH /v1/account/preferences. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record UserPreferencesPatchRequest(@NotNull @Min(1) @Max(1000) Integer dailyGoal) {}
