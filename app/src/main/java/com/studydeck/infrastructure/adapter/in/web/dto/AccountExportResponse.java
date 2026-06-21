package com.studydeck.infrastructure.adapter.in.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST response DTO for the GDPR account export endpoint (GET /v1/account/export).
 *
 * <p>Matches the {@code AccountExport} schema in the OpenAPI contract.
 */
public record AccountExportResponse(
    AccountSummary account,
    List<Object> decks,
    List<DocumentSummary> documents,
    Instant exportedAt) {

  /** Summary of the authenticated user's account fields. */
  public record AccountSummary(
      UUID id, String subject, String email, String displayName, Instant createdAt) {}

  /** Summary of a source document for the export payload. */
  public record DocumentSummary(
      UUID id, String title, String sourceType, String ingestStatus, Instant createdAt) {}
}
