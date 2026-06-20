package com.studydeck.infrastructure.adapter.in.web.dto.corpus;

import com.studydeck.domain.model.SourceDocument;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** REST response DTO for a source document. */
public record DocumentResponse(
    UUID id,
    String title,
    String sourceType,
    String mimeType,
    String originalFilename,
    Long sizeBytes,
    String ingestStatus,
    Map<String, Object> metadata,
    Instant createdAt,
    Instant updatedAt) {

  public static DocumentResponse from(SourceDocument doc) {
    return new DocumentResponse(
        doc.getId().value(),
        doc.getTitle(),
        doc.getSourceType(),
        doc.getMimeType(),
        doc.getOriginalFilename(),
        doc.getSizeBytes(),
        doc.getIngestStatus().name(),
        doc.getMetadata(),
        doc.getCreatedAt(),
        doc.getUpdatedAt());
  }
}
