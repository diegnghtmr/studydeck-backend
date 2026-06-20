package com.studydeck.domain.model;

import java.time.Instant;

/**
 * Metadata for a vector embedding associated with a {@link DocumentChunk}.
 *
 * <p>Records which embedding model + dimensions produced this vector. This prevents mixed-dimension
 * indexing in the pgvector table (a common production bug when switching embedding models).
 *
 * <p>The actual float[] vector is stored in Spring AI's {@code vector_store} table. This record
 * stores only metadata for query filtering and auditing.
 *
 * <p>Pure domain class — no Spring, no Jakarta EE, no Spring AI imports.
 */
public final class EmbeddingRecord {

  private final EmbeddingRecordId id;
  private final ChunkId chunkId;
  private final OwnerId ownerId;
  private final String embeddingModel;
  private final int dimensions;
  private final String provider;
  private final Instant createdAt;

  private EmbeddingRecord(
      EmbeddingRecordId id,
      ChunkId chunkId,
      OwnerId ownerId,
      String embeddingModel,
      int dimensions,
      String provider,
      Instant createdAt) {
    this.id = id;
    this.chunkId = chunkId;
    this.ownerId = ownerId;
    this.embeddingModel = embeddingModel;
    this.dimensions = dimensions;
    this.provider = provider;
    this.createdAt = createdAt;
  }

  public static EmbeddingRecord create(
      EmbeddingRecordId id,
      ChunkId chunkId,
      OwnerId ownerId,
      String embeddingModel,
      int dimensions,
      String provider,
      Instant now) {
    if (embeddingModel == null || embeddingModel.isBlank()) {
      throw new IllegalArgumentException("embeddingModel must not be blank");
    }
    if (dimensions <= 0) {
      throw new IllegalArgumentException("dimensions must be > 0");
    }
    return new EmbeddingRecord(id, chunkId, ownerId, embeddingModel, dimensions, provider, now);
  }

  public static EmbeddingRecord reconstitute(
      EmbeddingRecordId id,
      ChunkId chunkId,
      OwnerId ownerId,
      String embeddingModel,
      int dimensions,
      String provider,
      Instant createdAt) {
    return new EmbeddingRecord(
        id, chunkId, ownerId, embeddingModel, dimensions, provider, createdAt);
  }

  public EmbeddingRecordId getId() {
    return id;
  }

  public ChunkId getChunkId() {
    return chunkId;
  }

  public OwnerId getOwnerId() {
    return ownerId;
  }

  public String getEmbeddingModel() {
    return embeddingModel;
  }

  public int getDimensions() {
    return dimensions;
  }

  public String getProvider() {
    return provider;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
