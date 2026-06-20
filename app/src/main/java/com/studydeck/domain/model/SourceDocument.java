package com.studydeck.domain.model;

import java.time.Instant;
import java.util.Map;

/**
 * Domain model for a source document registered by an owner for RAG ingestion.
 *
 * <p>Lifecycle: PENDING → (ingest triggered) → RUNNING → COMPLETED | FAILED.
 *
 * <p>Pure domain class — no Spring, no Jakarta EE, no Spring AI imports.
 */
public final class SourceDocument {

  private final DocumentId id;
  private final OwnerId ownerId;
  private String title;
  private final String sourceType;
  private final String mimeType;
  private final String originalFilename;
  private final String textContent;
  private final String externalUrl;
  private final Long sizeBytes;
  private IngestStatus ingestStatus;
  private final Map<String, Object> metadata;
  private final Instant createdAt;
  private Instant updatedAt;

  private SourceDocument(
      DocumentId id,
      OwnerId ownerId,
      String title,
      String sourceType,
      String mimeType,
      String originalFilename,
      String textContent,
      String externalUrl,
      Long sizeBytes,
      IngestStatus ingestStatus,
      Map<String, Object> metadata,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.ownerId = ownerId;
    this.title = title;
    this.sourceType = sourceType;
    this.mimeType = mimeType;
    this.originalFilename = originalFilename;
    this.textContent = textContent;
    this.externalUrl = externalUrl;
    this.sizeBytes = sizeBytes;
    this.ingestStatus = ingestStatus;
    this.metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /** Factory method for creating a new document (not yet ingested). */
  public static SourceDocument create(
      DocumentId id,
      OwnerId ownerId,
      String title,
      String sourceType,
      String mimeType,
      String originalFilename,
      String textContent,
      String externalUrl,
      Long sizeBytes,
      Map<String, Object> metadata,
      Instant now) {
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("Document title must not be blank");
    }
    if (sourceType == null || sourceType.isBlank()) {
      throw new IllegalArgumentException("sourceType must not be blank");
    }
    return new SourceDocument(
        id,
        ownerId,
        title,
        sourceType,
        mimeType,
        originalFilename,
        textContent,
        externalUrl,
        sizeBytes,
        IngestStatus.PENDING,
        metadata,
        now,
        now);
  }

  /** Reconstitutes a document from persistence (all fields supplied). */
  public static SourceDocument reconstitute(
      DocumentId id,
      OwnerId ownerId,
      String title,
      String sourceType,
      String mimeType,
      String originalFilename,
      String textContent,
      String externalUrl,
      Long sizeBytes,
      IngestStatus ingestStatus,
      Map<String, Object> metadata,
      Instant createdAt,
      Instant updatedAt) {
    return new SourceDocument(
        id,
        ownerId,
        title,
        sourceType,
        mimeType,
        originalFilename,
        textContent,
        externalUrl,
        sizeBytes,
        ingestStatus,
        metadata,
        createdAt,
        updatedAt);
  }

  public void markRunning(Instant now) {
    this.ingestStatus = IngestStatus.RUNNING;
    this.updatedAt = now;
  }

  public void markCompleted(Instant now) {
    this.ingestStatus = IngestStatus.COMPLETED;
    this.updatedAt = now;
  }

  public void markFailed(Instant now) {
    this.ingestStatus = IngestStatus.FAILED;
    this.updatedAt = now;
  }

  public DocumentId getId() {
    return id;
  }

  public OwnerId getOwnerId() {
    return ownerId;
  }

  public String getTitle() {
    return title;
  }

  public String getSourceType() {
    return sourceType;
  }

  public String getMimeType() {
    return mimeType;
  }

  public String getOriginalFilename() {
    return originalFilename;
  }

  public String getTextContent() {
    return textContent;
  }

  public String getExternalUrl() {
    return externalUrl;
  }

  public Long getSizeBytes() {
    return sizeBytes;
  }

  public IngestStatus getIngestStatus() {
    return ingestStatus;
  }

  public Map<String, Object> getMetadata() {
    return metadata;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
