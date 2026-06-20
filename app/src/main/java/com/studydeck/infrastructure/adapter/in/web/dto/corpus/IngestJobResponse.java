package com.studydeck.infrastructure.adapter.in.web.dto.corpus;

import com.studydeck.domain.model.IngestJob;
import java.util.UUID;

/** REST response DTO for an ingest job (202 Accepted). */
public record IngestJobResponse(UUID jobId, UUID documentId, String status) {

  public static IngestJobResponse from(IngestJob job) {
    return new IngestJobResponse(
        job.getId().value(), job.getDocumentId().value(), job.getStatus().name());
  }
}
