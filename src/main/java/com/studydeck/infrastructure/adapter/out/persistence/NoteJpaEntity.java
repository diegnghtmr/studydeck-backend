package com.studydeck.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity for the {@code note} table.
 *
 * <p>NoteContent is stored as JSONB — deserialized by {@link NoteContentConverter}.
 */
@Entity
@Table(name = "note")
class NoteJpaEntity {

  @Id private UUID id;

  @Column(name = "deck_id", nullable = false)
  private UUID deckId;

  @Column(name = "note_type", nullable = false)
  private String noteType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String content;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "tags", columnDefinition = "text[]")
  private List<String> tags = new ArrayList<>();

  @Column(nullable = false)
  private int version;

  @Column(name = "content_hash")
  private String contentHash;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected NoteJpaEntity() {}

  // --- Getters and setters ---

  UUID getId() {
    return id;
  }

  void setId(UUID id) {
    this.id = id;
  }

  UUID getDeckId() {
    return deckId;
  }

  void setDeckId(UUID deckId) {
    this.deckId = deckId;
  }

  String getNoteType() {
    return noteType;
  }

  void setNoteType(String noteType) {
    this.noteType = noteType;
  }

  String getContent() {
    return content;
  }

  void setContent(String content) {
    this.content = content;
  }

  List<String> getTags() {
    return tags;
  }

  void setTags(List<String> tags) {
    this.tags = (tags != null) ? new ArrayList<>(tags) : new ArrayList<>();
  }

  int getVersion() {
    return version;
  }

  void setVersion(int version) {
    this.version = version;
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

  String getContentHash() {
    return contentHash;
  }

  void setContentHash(String contentHash) {
    this.contentHash = contentHash;
  }
}
