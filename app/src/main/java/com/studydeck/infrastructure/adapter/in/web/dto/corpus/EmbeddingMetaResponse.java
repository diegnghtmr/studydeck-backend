package com.studydeck.infrastructure.adapter.in.web.dto.corpus;

import com.studydeck.domain.model.EmbeddingRecord;
import java.time.Instant;
import java.util.UUID;

/** REST response DTO for embedding metadata. */
public record EmbeddingMetaResponse(
    UUID id, UUID chunkId, String provider, String model, int dimensions, Instant createdAt) {

  public static EmbeddingMetaResponse from(EmbeddingRecord record) {
    return new EmbeddingMetaResponse(
        record.getId().value(),
        record.getChunkId().value(),
        record.getProvider(),
        record.getEmbeddingModel(),
        record.getDimensions(),
        record.getCreatedAt());
  }
}
