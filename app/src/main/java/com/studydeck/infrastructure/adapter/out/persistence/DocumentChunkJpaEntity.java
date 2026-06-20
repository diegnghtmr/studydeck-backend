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
 * JPA entity for the {@code document_chunk} table.
 *
 * <p>Domain model is kept pure; this entity lives exclusively in the infrastructure layer.
 */
@Entity
@Table(name = "document_chunk")
class DocumentChunkJpaEntity {

  @Id private UUID id;

  @Column(name = "document_id", nullable = false)
  private UUID documentId;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Column(nullable = false)
  private int ordinal;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(name = "token_count")
  private Integer tokenCount;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata", columnDefinition = "JSONB")
  private String metadata;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected DocumentChunkJpaEntity() {}

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

  int getOrdinal() {
    return ordinal;
  }

  void setOrdinal(int ordinal) {
    this.ordinal = ordinal;
  }

  String getContent() {
    return content;
  }

  void setContent(String content) {
    this.content = content;
  }

  Integer getTokenCount() {
    return tokenCount;
  }

  void setTokenCount(Integer tokenCount) {
    this.tokenCount = tokenCount;
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
}
