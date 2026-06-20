package com.studydeck.infrastructure.adapter.in.web.dto.corpus;

import java.util.Map;

/** REST request DTO for triggering document ingestion. */
public record RagIngestRequest(
    ChunkingOptions chunking, String embeddingProvider, Map<String, Object> metadata) {

  public record ChunkingOptions(String strategy, Integer targetSize, Integer overlap) {}
}
