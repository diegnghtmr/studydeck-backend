package com.studydeck.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity for the {@code source_document} table.
 *
 * <p>Domain model is kept pure; this entity lives exclusively in the infrastructure layer.
 */
@Entity
@Table(name = "source_document")
class SourceDocumentJpaEntity {

  @Id private UUID id;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Column(nullable = false)
  private String title;

  @Column(name = "source_type", nullable = false)
  private String sourceType;

  @Column(name = "mime_type")
  private String mimeType;

  @Column(name = "original_filename")
  private String originalFilename;

  @Column(name = "text_content", columnDefinition = "TEXT")
  private String textContent;

  @Column(name = "external_url")
  private String externalUrl;

  @Column(name = "size_bytes")
  private Long sizeBytes;

  @Column(name = "ingest_status", nullable = false)
  private String ingestStatus;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata", columnDefinition = "JSONB")
  private String metadata;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected SourceDocumentJpaEntity() {}

  UUID getId() {
    return id;
  }

  void setId(UUID id) {
    this.id = id;
  }

  UUID getOwnerId() {
    return ownerId;
  }

  void setOwnerId(UUID ownerId) {
    this.ownerId = ownerId;
  }

  String getTitle() {
    return title;
  }

  void setTitle(String title) {
    this.title = title;
  }

  String getSourceType() {
    return sourceType;
  }

  void setSourceType(String sourceType) {
    this.sourceType = sourceType;
  }

  String getMimeType() {
    return mimeType;
  }

  void setMimeType(String mimeType) {
    this.mimeType = mimeType;
  }

  String getOriginalFilename() {
    return originalFilename;
  }

  void setOriginalFilename(String originalFilename) {
    this.originalFilename = originalFilename;
  }

  String getTextContent() {
    return textContent;
  }

  void setTextContent(String textContent) {
    this.textContent = textContent;
  }

  String getExternalUrl() {
    return externalUrl;
  }

  void setExternalUrl(String externalUrl) {
    this.externalUrl = externalUrl;
  }

  Long getSizeBytes() {
    return sizeBytes;
  }

  void setSizeBytes(Long sizeBytes) {
    this.sizeBytes = sizeBytes;
  }

  String getIngestStatus() {
    return ingestStatus;
  }

  void setIngestStatus(String ingestStatus) {
    this.ingestStatus = ingestStatus;
  }

  String getMetadata() {
    return metadata;
  }

  void setMetadata(String metadata) {
    this.metadata = metadata;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  Instant getUpdatedAt() {
    return updatedAt;
  }

  void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
