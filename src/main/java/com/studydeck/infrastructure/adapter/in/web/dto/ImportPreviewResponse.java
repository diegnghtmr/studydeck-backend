package com.studydeck.infrastructure.adapter.in.web.dto;

import java.util.List;

/** Response DTO for POST /v1/imports/flashcards:preview */
public record ImportPreviewResponse(boolean valid, SummaryDto summary, List<String> warnings) {

  public record SummaryDto(
      String deckTitle, int totalNotes, int predictedCards, int duplicateCandidates) {}
}
