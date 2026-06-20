package com.studydeck.infrastructure.adapter.in.web.dto.corpus;

import com.studydeck.domain.model.DocumentChunk;
import java.util.Map;
import java.util.UUID;

/** REST response DTO for a document chunk. */
public record ChunkResponse(
    UUID id,
    UUID documentId,
    int ordinal,
    Integer tokenCount,
    String content,
    Map<String, Object> metadata) {

  public static ChunkResponse from(DocumentChunk chunk) {
    return new ChunkResponse(
        chunk.getId().value(),
        chunk.getDocumentId().value(),
        chunk.getOrdinal(),
        chunk.getTokenCount(),
        chunk.getContent(),
        chunk.getMetadata());
  }
}
