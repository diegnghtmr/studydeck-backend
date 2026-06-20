package com.studydeck.domain.model;

import java.time.Instant;
import java.util.Map;

/**
 * Domain model for a text chunk produced by the ETL pipeline.
 *
 * <p>Ordinal is 0-based and unique per document.
 *
 * <p>Pure domain class — no Spring, no Jakarta EE, no Spring AI imports.
 */
public final class DocumentChunk {

  private final ChunkId id;
  private final DocumentId documentId;
  private final OwnerId ownerId;
  private final int ordinal;
  private final String content;
  private final Integer tokenCount;
  private final Map<String, Object> metadata;
  private final Instant createdAt;

  private DocumentChunk(
      ChunkId id,
      DocumentId documentId,
      OwnerId ownerId,
      int ordinal,
      String content,
      Integer tokenCount,
      Map<String, Object> metadata,
      Instant createdAt) {
    this.id = id;
    this.documentId = documentId;
    this.ownerId = ownerId;
    this.ordinal = ordinal;
    this.content = content;
    this.tokenCount = tokenCount;
    this.metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    this.createdAt = createdAt;
  }

  public static DocumentChunk create(
      ChunkId id,
      DocumentId documentId,
      OwnerId ownerId,
      int ordinal,
      String content,
      Integer tokenCount,
      Map<String, Object> metadata,
      Instant now) {
    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("Chunk content must not be blank");
    }
    if (ordinal < 0) {
      throw new IllegalArgumentException("Chunk ordinal must be >= 0");
    }
    return new DocumentChunk(id, documentId, ownerId, ordinal, content, tokenCount, metadata, now);
  }

  public static DocumentChunk reconstitute(
      ChunkId id,
      DocumentId documentId,
      OwnerId ownerId,
      int ordinal,
      String content,
      Integer tokenCount,
      Map<String, Object> metadata,
      Instant createdAt) {
    return new DocumentChunk(
        id, documentId, ownerId, ordinal, content, tokenCount, metadata, createdAt);
  }

  public ChunkId getId() {
    return id;
  }

  public DocumentId getDocumentId() {
    return documentId;
  }

  public OwnerId getOwnerId() {
    return ownerId;
  }

  public int getOrdinal() {
    return ordinal;
  }

  public String getContent() {
    return content;
  }

  public Integer getTokenCount() {
    return tokenCount;
  }

  public Map<String, Object> getMetadata() {
    return metadata;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
