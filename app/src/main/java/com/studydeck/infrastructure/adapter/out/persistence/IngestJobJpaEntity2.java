package com.studydeck.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code ingest_job} table (RAG ingestion jobs).
 *
 * <p>Named {@code IngestJobJpaEntity2} to avoid collision with {@code ImportJobJpaEntity} (the
 * flashcard import job entity from V4 migration).
 */
@Entity
@Table(name = "ingest_job")
class IngestJobJpaEntity2 {

  @Id private UUID id;

  @Column(name = "document_id", nullable = false)
  private UUID documentId;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Column(nullable = false)
  private String status;

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "chunks_produced")
  private Integer chunksProduced;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected IngestJobJpaEntity2() {}

  UUID getId() {
    return id;
  }

  void setId(UUID id) {
    this.id = id;
  }

  UUID getDocumentId() {
    return documentId;
  }

  void setDocumentId(UUID documentId) {
    this.documentId = documentId;
  }

  UUID getOwnerId() {
    return ownerId;
  }

  void setOwnerId(UUID ownerId) {
    this.ownerId = ownerId;
  }

  String getStatus() {
    return status;
  }

  void setStatus(String status) {
    this.status = status;
  }

  String getErrorMessage() {
    return errorMessage;
  }

  void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  Integer getChunksProduced() {
    return chunksProduced;
  }

  void setChunksProduced(Integer chunksProduced) {
    this.chunksProduced = chunksProduced;
  }

  Instant getStartedAt() {
    return startedAt;
  }

  void setStartedAt(Instant startedAt) {
    this.startedAt = startedAt;
  }

  Instant getFinishedAt() {
    return finishedAt;
  }

  void setFinishedAt(Instant finishedAt) {
    this.finishedAt = finishedAt;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
