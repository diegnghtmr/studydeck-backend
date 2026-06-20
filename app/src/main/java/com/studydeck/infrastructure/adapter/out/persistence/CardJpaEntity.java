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
 * JPA entity for the {@code card} table.
 *
 * <p>Prompt and answer payloads are stored as JSONB strings; deserialization is handled by {@link
 * PersistenceMapper} using an infrastructure-owned ObjectMapper.
 */
@Entity
@Table(name = "card")
class CardJpaEntity {

  @Id private UUID id;

  @Column(name = "note_id", nullable = false)
  private UUID noteId;

  @Column(name = "note_type", nullable = false)
  private String noteType;

  @Column(name = "card_variant", nullable = false)
  private String cardVariant;

  @Column(nullable = false)
  private int ordinal;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "prompt_payload", nullable = false, columnDefinition = "jsonb")
  private String promptPayload;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "answer_payload", nullable = false, columnDefinition = "jsonb")
  private String answerPayload;

  @Column(nullable = false)
  private boolean suspended;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected CardJpaEntity() {}

  // --- Getters and setters ---

  UUID getId() {
    return id;
  }

  void setId(UUID id) {
    this.id = id;
  }

  UUID getNoteId() {
    return noteId;
  }

  void setNoteId(UUID noteId) {
    this.noteId = noteId;
  }

  String getNoteType() {
    return noteType;
  }

  void setNoteType(String noteType) {
    this.noteType = noteType;
  }

  String getCardVariant() {
    return cardVariant;
  }

  void setCardVariant(String cardVariant) {
    this.cardVariant = cardVariant;
  }

  int getOrdinal() {
    return ordinal;
  }

  void setOrdinal(int ordinal) {
    this.ordinal = ordinal;
  }

  String getPromptPayload() {
    return promptPayload;
  }

  void setPromptPayload(String promptPayload) {
    this.promptPayload = promptPayload;
  }

  String getAnswerPayload() {
    return answerPayload;
  }

  void setAnswerPayload(String answerPayload) {
    this.answerPayload = answerPayload;
  }

  boolean isSuspended() {
    return suspended;
  }

  void setSuspended(boolean suspended) {
    this.suspended = suspended;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
