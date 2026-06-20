package com.studydeck.domain.model;

import java.time.Instant;

/**
 * Domain model tracking an async ETL ingest operation.
 *
 * <p>POST /v1/documents/{id}/ingest returns 202 Accepted with an {@link IngestJob} (PENDING). The
 * async ETL process transitions the job to RUNNING → COMPLETED | FAILED.
 *
 * <p>Pure domain class — no Spring, no Jakarta EE, no Spring AI imports.
 */
public final class IngestJob {

  private final IngestJobId id;
  private final DocumentId documentId;
  private final OwnerId ownerId;
  private IngestStatus status;
  private String errorMessage;
  private Integer chunksProduced;
  private Instant startedAt;
  private Instant finishedAt;
  private final Instant createdAt;

  private IngestJob(
      IngestJobId id,
      DocumentId documentId,
      OwnerId ownerId,
      IngestStatus status,
      String errorMessage,
      Integer chunksProduced,
      Instant startedAt,
      Instant finishedAt,
      Instant createdAt) {
    this.id = id;
    this.documentId = documentId;
    this.ownerId = ownerId;
    this.status = status;
    this.errorMessage = errorMessage;
    this.chunksProduced = chunksProduced;
    this.startedAt = startedAt;
    this.finishedAt = finishedAt;
    this.createdAt = createdAt;
  }

  /** Creates a new ingest job in PENDING state. */
  public static IngestJob create(
      IngestJobId id, DocumentId documentId, OwnerId ownerId, Instant now) {
    return new IngestJob(
        id, documentId, ownerId, IngestStatus.PENDING, null, null, null, null, now);
  }

  /** Reconstitutes from persistence. */
  public static IngestJob reconstitute(
      IngestJobId id,
      DocumentId documentId,
      OwnerId ownerId,
      IngestStatus status,
      String errorMessage,
      Integer chunksProduced,
      Instant startedAt,
      Instant finishedAt,
      Instant createdAt) {
    return new IngestJob(
        id,
        documentId,
        ownerId,
        status,
        errorMessage,
        chunksProduced,
        startedAt,
        finishedAt,
        createdAt);
  }

  public void markRunning(Instant now) {
    this.status = IngestStatus.RUNNING;
    this.startedAt = now;
  }

  public void markCompleted(int chunksProduced, Instant now) {
    this.status = IngestStatus.COMPLETED;
    this.chunksProduced = chunksProduced;
    this.finishedAt = now;
  }

  public void markFailed(String errorMessage, Instant now) {
    this.status = IngestStatus.FAILED;
    this.errorMessage = errorMessage;
    this.finishedAt = now;
  }

  public IngestJobId getId() {
    return id;
  }

  public DocumentId getDocumentId() {
    return documentId;
  }

  public OwnerId getOwnerId() {
    return ownerId;
  }

  public IngestStatus getStatus() {
    return status;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public Integer getChunksProduced() {
    return chunksProduced;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getFinishedAt() {
    return finishedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
