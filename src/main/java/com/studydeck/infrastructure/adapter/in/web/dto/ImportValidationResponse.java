package com.studydeck.infrastructure.adapter.in.web.dto;

import java.util.List;

/** Response DTO for POST /v1/imports/flashcards:validate */
public record ImportValidationResponse(
    boolean valid, List<ViolationDto> errors, List<String> warnings) {

  public record ViolationDto(String field, String message, String code) {}
}
