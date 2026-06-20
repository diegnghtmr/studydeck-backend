package com.studydeck.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code embedding_record} table.
 *
 * <p>Stores metadata (model name, dimensions) for each vector embedding — not the actual vector
 * (that lives in {@code vector_store} managed by Spring AI).
 */
@Entity
@Table(name = "embedding_record")
class EmbeddingRecordJpaEntity {

  @Id private UUID id;

  @Column(name = "chunk_id", nullable = false)
  private UUID chunkId;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Column(name = "embedding_model", nullable = false)
  private String embeddingModel;

  @Column(nullable = false)
  private int dimensions;

  @Column(nullable = false)
  private String provider;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected EmbeddingRecordJpaEntity() {}

  UUID getId() {
    return id;
  }

  void setId(UUID id) {
    this.id = id;
  }

  UUID getChunkId() {
    return chunkId;
  }

  void setChunkId(UUID chunkId) {
    this.chunkId = chunkId;
  }

  UUID getOwnerId() {
    return ownerId;
  }

  void setOwnerId(UUID ownerId) {
    this.ownerId = ownerId;
  }

  String getEmbeddingModel() {
    return embeddingModel;
  }

  void setEmbeddingModel(String embeddingModel) {
    this.embeddingModel = embeddingModel;
  }

  int getDimensions() {
    return dimensions;
  }

  void setDimensions(int dimensions) {
    this.dimensions = dimensions;
  }

  String getProvider() {
    return provider;
  }

  void setProvider(String provider) {
    this.provider = provider;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
